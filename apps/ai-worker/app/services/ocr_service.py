"""OCR Service - handles image/PDF processing and trade extraction."""

import base64
import io
import json
import re
import sys
from datetime import date
from decimal import Decimal
from enum import Enum
from pathlib import Path

import structlog
from PIL import Image

from app.config import LlmProvider, OcrProvider, get_settings
from app.metrics import record_ocr_fallback
from app.models.schemas import Currency, OcrRequest, OcrResponse, ParsedTrade, TradeSide
from app.services.llm_service import LlmService
from app.services.tesseract_service import TesseractService

# Try to import pdf2image for PDF support
try:
    from pdf2image import convert_from_bytes
    PDF_SUPPORT = True
except ImportError:
    PDF_SUPPORT = False
    convert_from_bytes = None  # type: ignore

try:
    from pypdf import PdfReader
    PDF_TEXT_SUPPORT = True
except ImportError:
    PDF_TEXT_SUPPORT = False
    PdfReader = None  # type: ignore

logger = structlog.get_logger()

TRADE_LINE_PATTERN = re.compile(
    r"^\s*"
    r"(?P<trade_date>\d{2,4}/\d{1,2}/\d{1,2})\s+"
    r"(?P<settlement_date>\d{2,4}/\d{1,2}/\d{1,2})\s+"
    r"(?P<market>\S+)\s+"
    r"(?P<currency>TWD|USD)\s+"
    r"(?P<side>買進|賣出|買|賣|BUY|SELL)\s+"
    r"(?P<ticker>[A-Z0-9][A-Z0-9.-]{1,12})\s+"
    r"(?P<stock_name>.+?)\s+"
    r"(?P<quantity>[\d,]+(?:\.0+)?)\s+"
    r"(?P<price>[\d,]+(?:\.\d+)?)"
    r"(?:\s+(?P<rest>.*))?\s*$",
    re.IGNORECASE,
)

AMOUNT_TOKEN_PATTERN = re.compile(r"\(?-?[\d,]+(?:\.\d+)?\)?")
TRADE_SIGNAL_PATTERN = re.compile(
    r"(?:^|\s)(?:買進|賣出|買|賣|BUY|SELL)\s+[A-Z0-9][A-Z0-9.-]{1,12}(?:\s|$)",
    re.IGNORECASE,
)

PDF_PAGE_USE_TEXT = "use_text"
PDF_PAGE_USE_TEXT_AND_VISION = "use_text_and_vision"
PDF_PAGE_USE_VISION = "use_vision"
PDF_PAGE_SKIP = "skip"

TRADE_PAGE_KEYWORDS = (
    "成交日期",
    "交割日期",
    "買賣",
    "股票代號",
    "商品代號",
    "證券代號",
    "數量",
    "成交價",
    "手續費",
    "交易稅",
    "淨收付",
    "交易明細",
)

NON_TRADE_PAGE_KEYWORDS = (
    "帳戶總覽",
    "資產總覽",
    "現值折合台幣",
    "比重",
    "客服專線",
    "重要聲明",
    "注意事項",
    "風險預告",
)


class OcrMethod(str, Enum):
    """OCR method used for extraction."""
    TESSERACT = "tesseract"
    VISION_LLM = "vision_llm"


class PdfPasswordRequiredError(Exception):
    """Raised when a PDF requires a password and none was provided."""


class PdfPasswordInvalidError(Exception):
    """Raised when the provided PDF password cannot open the file."""


# System prompts
VISION_EXTRACT_PROMPT = """你是一個專業的 OCR 文字擷取助手。請從圖片中擷取所有可見的文字內容。

規則：
1. 保持原始的表格格式和對齊
2. 保留所有數字、日期、金額
3. 不要翻譯或修改任何內容
4. 如果有多欄或多列，用適當的分隔符號表示
5. 日期格式保持原樣（民國或西元）

請擷取這張圖片中的所有文字內容，特別注意交易相關的資訊（股票代碼、買賣、數量、價格、日期等）。"""


def get_parse_prompt(broker_hint: str) -> str:
    """Get the system prompt for parsing trade data."""
    return f"""你是一個專業的交易資料解析助手。請將 OCR 擷取的文字轉換成結構化的交易資料。
{broker_hint}

**輸出格式 (JSON):**
```json
{{
    "trades": [
        {{
            "ticker": "股票代碼 (如 2330, AAPL)",
            "stock_name": "股票名稱 (如有)",
            "side": "BUY 或 SELL",
            "quantity": 數量 (整數),
            "price": 價格 (數字),
            "trade_date": "YYYY-MM-DD (成交日，西元)",
            "settlement_date": "YYYY-MM-DD (交割日，西元，如有)",
            "currency": "TWD 或 USD",
            "fee": 手續費 (數字或 null),
            "tax": 稅金 (數字或 null),
            "confidence": 0.0-1.0 (解析信心度),
            "warnings": ["警告訊息"],
            "raw_line": "原始文字行"
        }}
    ],
    "broker_detected": "偵測到的券商名稱 或 null",
    "original_date_format": "ROC 或 AD",
    "warnings": ["全域警告訊息"]
}}
```

**規則:**
1. 民國年轉西元年: 民國年 + 1911 = 西元年
2. 買入 = BUY, 賣出 = SELL
3. 台股用 TWD, 美股用 USD
4. 成交日 (trade_date) 和交割日 (settlement_date) 通常是表格中的前兩個日期欄位
5. 如果某欄位無法確認，降低 confidence 分數並加入 warnings
6. 只輸出 JSON，不要其他文字"""



class OcrService:
    """Service for processing OCR and extracting trade data."""

    def __init__(self) -> None:
        """Initialize OCR service."""
        self.settings = get_settings()
        self.ocr_provider = self.settings.ocr_provider
        self.vision_provider = self._resolve_vision_provider()
        self.llm = LlmService(provider=self.vision_provider) if self.vision_provider else None
        self.tesseract = TesseractService()

    def _resolve_vision_provider(self) -> LlmProvider | None:
        if self.ocr_provider == OcrProvider.TESSERACT:
            return None
        if self.ocr_provider == OcrProvider.GEMINI:
            return LlmProvider.GEMINI
        if self.ocr_provider == OcrProvider.OLLAMA:
            return LlmProvider.OLLAMA
        if self.ocr_provider == OcrProvider.OPENAI:
            return LlmProvider.OPENAI
        # AUTO -> fallback to LLM_PROVIDER
        return self.settings.llm_provider

    async def process(self, content: bytes, request: OcrRequest) -> OcrResponse:
        """
        Process an image or PDF and extract trade data.
        
        Uses dual-path OCR:
        1. Try Tesseract first (free, local)
        2. Fallback to Vision LLM if Tesseract fails or low confidence

        Args:
            content: File content as bytes
            request: OCR request metadata

        Returns:
            OcrResponse with extracted trades
        """
        logger.info(
            "Processing OCR",
            filename=request.filename,
            content_type=request.content_type,
        )

        # Handle PDF: convert each page to an individual image
        page_images: list[bytes] = []  # List of per-page PNG bytes
        pdf_page_texts: list[str] = []
        image_bytes = content  # Fallback for non-PDF (single image)
        is_pdf = request.content_type == "application/pdf"

        if is_pdf:
            if not PDF_SUPPORT:
                logger.error("PDF support not available - pdf2image not installed")
                return OcrResponse(
                    raw_text="",
                    trades=[],
                    confidence=0.0,
                    warnings=["PDF 支援尚未安裝，請安裝 pdf2image 和 Poppler"],
                    ocr_method="none",
                )
            
            try:
                pdf_page_texts = self._extract_pdf_page_texts(content, request.pdf_password) or []
                logger.info("Converting PDF to images")
                poppler_path = self._resolve_poppler_path()
                kwargs = {
                    "dpi": self.settings.pdf_render_dpi,
                }
                if self.settings.pdf_max_pages and self.settings.pdf_max_pages > 0:
                    kwargs["first_page"] = 1
                    kwargs["last_page"] = self.settings.pdf_max_pages
                if request.pdf_password:
                    kwargs["userpw"] = request.pdf_password

                images = (
                    convert_from_bytes(content, poppler_path=poppler_path, **kwargs)
                    if poppler_path
                    else convert_from_bytes(content, **kwargs)
                )
                if not images:
                    raise ValueError("No pages extracted from PDF")

                max_pixels = self.settings.pdf_max_total_pixels
                if max_pixels and max_pixels > 0:
                    total_pixels = sum(img.width * img.height for img in images if isinstance(img, Image.Image))
                    if total_pixels > max_pixels:
                        raise ValueError("PDF exceeds max pixel limit")
                
                # Convert each page to PNG bytes individually (do NOT merge)
                for idx, img in enumerate(images):
                    buf = io.BytesIO()
                    img.save(buf, format="PNG")
                    page_images.append(buf.getvalue())

                if pdf_page_texts:
                    if len(pdf_page_texts) > len(page_images):
                        pdf_page_texts = pdf_page_texts[: len(page_images)]
                    elif len(pdf_page_texts) < len(page_images):
                        pdf_page_texts.extend([""] * (len(page_images) - len(pdf_page_texts)))
                
                logger.info("PDF converted to images", pages=len(page_images))
            except Exception as e:
                if self._is_pdf_password_error(e):
                    if request.pdf_password:
                        raise PdfPasswordInvalidError() from e
                    raise PdfPasswordRequiredError() from e
                logger.error("PDF conversion failed", error=str(e))
                return OcrResponse(
                    raw_text="",
                    trades=[],
                    confidence=0.0,
                    warnings=[f"PDF 轉換失敗: {str(e)}"],
                    ocr_method="none",
                )

        raw_text = ""
        ocr_method = OcrMethod.TESSERACT
        extraction_confidence = 0.0

        use_tesseract = self.ocr_provider in (OcrProvider.AUTO, OcrProvider.TESSERACT)
        allow_vision = (
            self.ocr_provider != OcrProvider.TESSERACT
            and (self.ocr_provider != OcrProvider.AUTO or self.settings.ocr_fallback_to_vision)
        )

        # Path A: Tesseract first (AUTO/TESSERACT)
        if use_tesseract and not self.tesseract.is_available() and allow_vision:
            record_ocr_fallback("tesseract_unavailable_to_vision")
        if use_tesseract and self.tesseract.is_available():
            try:
                # For PDF, run Tesseract on each page and combine
                if page_images:
                    page_texts = []
                    total_conf = 0.0
                    for idx, pg_bytes in enumerate(page_images):
                        pg_text, pg_conf = self.tesseract.extract_text(pg_bytes)
                        page_texts.append(pg_text)
                        total_conf += pg_conf
                    raw_text = "\n\n".join(page_texts)
                    extraction_confidence = total_conf / len(page_images) if page_images else 0.0
                else:
                    raw_text, extraction_confidence = self.tesseract.extract_text(image_bytes)

                logger.info(
                    "Tesseract extraction",
                    text_length=len(raw_text),
                    confidence=extraction_confidence,
                )

                # Check if Tesseract result is acceptable
                if (
                    extraction_confidence < self.settings.ocr_min_confidence
                    or len(raw_text) < self.settings.ocr_min_text_length
                ):
                    logger.info(
                        "Tesseract result below threshold, falling back to Vision LLM",
                        confidence=extraction_confidence,
                        text_length=len(raw_text),
                        min_confidence=self.settings.ocr_min_confidence,
                        min_length=self.settings.ocr_min_text_length,
                    )
                    record_ocr_fallback("tesseract_low_confidence_to_vision")
                    raw_text = ""  # Reset to trigger fallback

            except Exception as e:
                logger.warning("Tesseract extraction failed", error=str(e))
                record_ocr_fallback("tesseract_error_to_vision")
                raw_text = ""

        # Path B: Vision LLM (AUTO fallback or explicit provider)
        if not raw_text and allow_vision:
            if self.llm is None:
                raise RuntimeError("Vision provider not configured")
            logger.info("Using Vision LLM for OCR", provider=self.vision_provider.value)
            ocr_method = OcrMethod.VISION_LLM

            if page_images:
                # PDF: process each page individually through Vision LLM
                page_texts = []
                skipped_pages = 0
                text_layer_pages = 0
                vision_pages = 0
                page_actions = [
                    self.classify_pdf_page_text(pdf_page_texts[idx] if idx < len(pdf_page_texts) else "")
                    for idx in range(len(page_images))
                ]
                if page_actions and all(action == PDF_PAGE_SKIP for action, _reason in page_actions):
                    logger.info(
                        "PDF page filter selected no pages; processing all pages with Vision",
                        pages=len(page_actions),
                    )
                    record_ocr_fallback("page_filter_to_all_vision")
                    page_actions = [(PDF_PAGE_USE_VISION, "filter_selected_no_pages") for _ in page_actions]

                for idx, pg_bytes in enumerate(page_images):
                    page_text = pdf_page_texts[idx] if idx < len(pdf_page_texts) else ""
                    action, reason = page_actions[idx]
                    if action in (PDF_PAGE_USE_TEXT, PDF_PAGE_USE_TEXT_AND_VISION):
                        page_texts.append(page_text.strip())
                        text_layer_pages += 1
                        logger.info(
                            "PDF page text layer used",
                            page=idx + 1,
                            reason=reason,
                            text_length=len(page_text),
                        )
                        if action == PDF_PAGE_USE_TEXT:
                            continue

                    if action == PDF_PAGE_SKIP:
                        skipped_pages += 1
                        logger.info(
                            "Skipping PDF page without trade signals",
                            page=idx + 1,
                            reason=reason,
                            text_length=len(page_text),
                        )
                        continue

                    vision_pages += 1
                    logger.info("Processing PDF page with Vision LLM", page=idx + 1, total=len(page_images))
                    pg_base64 = base64.b64encode(pg_bytes).decode("utf-8")
                    pg_text = await self.llm.vision(
                        image_base64=pg_base64,
                        prompt=VISION_EXTRACT_PROMPT,
                        media_type="image/png",
                        operation="ocr_vision",
                    )
                    if pg_text and pg_text.strip():
                        page_texts.append(pg_text.strip())
                        logger.info("PDF page OCR done", page=idx + 1, text_length=len(pg_text))
                    else:
                        logger.warning("PDF page returned empty text", page=idx + 1)

                raw_text = "\n\n".join(page_texts)
                logger.info(
                    "All PDF pages processed",
                    pages=len(page_images),
                    vision_pages=vision_pages,
                    text_layer_pages=text_layer_pages,
                    skipped_pages=skipped_pages,
                    total_text_length=len(raw_text),
                )
            else:
                # Single image (non-PDF): original behavior
                image_base64 = base64.b64encode(image_bytes).decode("utf-8")

                media_type = request.content_type
                if media_type == "image/jpg":
                    media_type = "image/jpeg"

                raw_text = await self.llm.vision(
                    image_base64=image_base64,
                    prompt=VISION_EXTRACT_PROMPT,
                    media_type=media_type,
                    operation="ocr_vision",
                )
            extraction_confidence = 0.9  # Vision LLM generally high confidence

        # If no text extracted, return empty response
        if not raw_text:
            logger.warning("No text extracted from image")
            return OcrResponse(
                raw_text="",
                trades=[],
                confidence=0.0,
                warnings=["無法從圖片中提取文字"],
                ocr_method=ocr_method.value,
            )

        # Parse extracted text into trades
        response = await self.parse_text(raw_text, request.user_id, request.broker)
        response.ocr_method = ocr_method.value
        return response

    def _resolve_poppler_path(self) -> str | None:
        if not sys.platform.startswith("win"):
            return None

        candidates = [
            Path("C:/poppler/Library/bin"),
            Path("C:/poppler/poppler-25.12.0/Library/bin"),
        ]
        for candidate in candidates:
            if candidate.exists():
                return str(candidate)
        return None

    def _extract_pdf_page_texts(self, content: bytes, pdf_password: str | None) -> list[str] | None:
        """Extract per-page PDF text layer for cheap page filtering before Vision OCR."""
        if not PDF_TEXT_SUPPORT or PdfReader is None:
            logger.info("PDF text layer extraction unavailable")
            return None

        try:
            reader = PdfReader(io.BytesIO(content))
            if reader.is_encrypted:
                if not pdf_password:
                    logger.info("PDF text layer extraction skipped: password required")
                    return None
                decrypt_result = reader.decrypt(pdf_password)
                if decrypt_result == 0:
                    logger.info("PDF text layer extraction skipped: invalid password")
                    return None

            max_pages = self.settings.pdf_max_pages if self.settings.pdf_max_pages > 0 else len(reader.pages)
            texts: list[str] = []
            for page in list(reader.pages)[:max_pages]:
                try:
                    texts.append(page.extract_text() or "")
                except Exception as e:
                    logger.warning("PDF page text extraction failed", error=str(e))
                    texts.append("")

            logger.info(
                "PDF text layer extracted",
                pages=len(texts),
                pages_with_text=sum(1 for text in texts if text.strip()),
            )
            return texts
        except Exception as e:
            logger.warning("PDF text layer extraction failed", error=str(e))
            return None

    @staticmethod
    def _is_pdf_password_error(exc: Exception) -> bool:
        message = str(exc).lower()
        return any(
            token in message
            for token in (
                "password",
                "encrypted",
                "owner/user password",
                "incorrect password",
                "invalid password",
            )
        )

    async def parse_text(
        self,
        text: str,
        user_id: str,
        broker: str | None = None,
    ) -> OcrResponse:
        """
        Parse raw text into structured trade data using LLM.

        Args:
            text: Raw text from OCR
            user_id: User ID
            broker: Optional broker hint

        Returns:
            OcrResponse with parsed trades
        """
        logger.info("Parsing text", user_id=user_id, text_length=len(text), broker=broker)

        # 限制文字長度，避免超過 LLM token 限制
        # Gemini 2.0 Flash 總 token 限制約 32K，保留更多空間給輸出
        original_text = text
        trade_lines = self.extract_trade_lines(text)
        table_fallback_trades, table_warnings = self.parse_broker_statement_table_fallback(text)
        table_total_matched = table_fallback_trades and not any(
            "does not match" in warning for warning in table_warnings
        )
        if table_total_matched:
            logger.info(
                "Broker statement table fallback used",
                user_id=user_id,
                trades_count=len(table_fallback_trades),
                totals_matched=True,
            )
            avg_confidence = sum(t.confidence for t in table_fallback_trades) / len(table_fallback_trades)
            return OcrResponse(
                raw_text=original_text,
                trades=table_fallback_trades,
                confidence=avg_confidence,
                warnings=table_warnings,
                broker_detected=broker or self.detect_broker(original_text),
                original_date_format="AD",
            )
        if table_fallback_trades:
            logger.warning(
                "Broker statement table fallback totals mismatch; trying LLM parser",
                user_id=user_id,
                trades_count=len(table_fallback_trades),
                warnings=table_warnings,
            )
            record_ocr_fallback("table_totals_mismatch_to_llm")

        parser_text = self.build_trade_parser_text(text, trade_lines)
        if trade_lines:
            logger.info("Trade-like lines extracted", user_id=user_id, line_count=len(trade_lines))

        max_text_length = 8000  # 約 2600 tokens，保守設定以確保輸出空間充足
        if len(parser_text) > max_text_length:
            logger.warning("Text too long, truncating", original_length=len(parser_text), max_length=max_text_length)
            parser_text = parser_text[:max_text_length] + "\n\n[文字已截斷...]"

        broker_hint = f"券商: {broker}" if broker else "券商未知"

        messages = [
            {"role": "system", "content": get_parse_prompt(broker_hint)},
            {"role": "user", "content": f"請解析以下交易資料：\n\n{parser_text}"},
        ]

        response_text = await self.llm.chat(
            messages,
            temperature=0.1,
            max_tokens=8192,
            json_mode=True,
            operation="ocr_parse",
        )

        # Parse LLM response
        try:
            # Clean LLM response: remove markdown code blocks
            cleaned = response_text.strip()
            if cleaned.startswith("```"):
                # Remove ```json or ``` prefix and trailing ```
                cleaned = re.sub(r'^```(?:json)?\s*', '', cleaned)
                cleaned = re.sub(r'\s*```$', '', cleaned)
            
            # Try to extract JSON from response (in case there's extra text)
            json_match = re.search(r'\{[\s\S]*\}', cleaned)
            if json_match:
                json_str = json_match.group()
            else:
                json_str = cleaned
            
            # Remove trailing commas (common LLM mistake)
            json_str = re.sub(r',\s*([}\]])', r'\1', json_str)
            
            result = json.loads(json_str)
        except json.JSONDecodeError as e:
            logger.error(
                "Failed to parse LLM response as JSON",
                error=str(e),
                response_preview=response_text[:500] if response_text else None,
            )
            fallback_trades = self.parse_trade_lines_fallback(trade_lines) if trade_lines else []
            if table_fallback_trades:
                record_ocr_fallback("llm_json_to_table_parser")
                logger.info(
                    "Broker statement table fallback used after JSON parse failure",
                    user_id=user_id,
                    trades_count=len(table_fallback_trades),
                )
                avg_confidence = sum(t.confidence for t in table_fallback_trades) / len(table_fallback_trades)
                return OcrResponse(
                    raw_text=original_text,
                    trades=table_fallback_trades,
                    confidence=avg_confidence,
                    warnings=[
                        "LLM JSON parse failed; broker statement table fallback was used.",
                        *table_warnings,
                    ],
                    broker_detected=broker or self.detect_broker(original_text),
                    original_date_format="AD",
                )
            if fallback_trades:
                record_ocr_fallback("llm_json_to_rule_parser")
                logger.info(
                    "Rule-based trade fallback used after JSON parse failure",
                    user_id=user_id,
                    trades_count=len(fallback_trades),
                )
                avg_confidence = sum(t.confidence for t in fallback_trades) / len(fallback_trades)
                return OcrResponse(
                    raw_text=original_text,
                    trades=fallback_trades,
                    confidence=avg_confidence,
                    warnings=["LLM JSON parse failed; rule-based broker statement fallback was used."],
                )
            return OcrResponse(
                raw_text=original_text,
                trades=[],
                confidence=0.0,
                warnings=[f"LLM 回傳的格式無法解析: {str(e)}"],
            )

        # Convert to Pydantic models
        trades: list[ParsedTrade] = []
        for trade_data in result.get("trades", []):
            try:
                trade = ParsedTrade(
                    ticker=str(trade_data.get("ticker", "")),
                    side=TradeSide(trade_data.get("side", "BUY")),
                    quantity=int(trade_data.get("quantity", 0)),
                    price=Decimal(str(trade_data.get("price", 0))),
                    trade_date=date.fromisoformat(trade_data.get("trade_date", "1970-01-01")),
                    settlement_date=date.fromisoformat(trade_data["settlement_date"]) if trade_data.get("settlement_date") else None,
                    currency=Currency(trade_data.get("currency", "TWD")),
                    fee=Decimal(str(trade_data["fee"])) if trade_data.get("fee") else None,
                    tax=Decimal(str(trade_data["tax"])) if trade_data.get("tax") else None,
                    stock_name=trade_data.get("stock_name"),
                    confidence=float(trade_data.get("confidence", 1.0)),
                    warnings=trade_data.get("warnings", []),
                    raw_line=trade_data.get("raw_line"),
                )
                trades.append(trade)
            except Exception as e:
                logger.warning("Failed to parse trade", error=str(e), trade_data=trade_data)

        warnings = result.get("warnings", [])
        if not trades and table_fallback_trades:
            record_ocr_fallback("llm_empty_to_table_parser")
            trades = table_fallback_trades
            logger.info("Broker statement table fallback used", user_id=user_id, trades_count=len(trades))
            warnings = [
                *warnings,
                "LLM parser returned no trades; broker statement table fallback was used.",
                *table_warnings,
            ]
        if not trades and trade_lines:
            trades = self.parse_trade_lines_fallback(trade_lines)
            if trades:
                record_ocr_fallback("llm_empty_to_rule_parser")
                logger.info("Rule-based trade fallback used", user_id=user_id, trades_count=len(trades))
                warnings = [
                    *warnings,
                    "LLM parser returned no trades; rule-based broker statement fallback was used.",
                ]

        # Calculate overall confidence
        avg_confidence = sum(t.confidence for t in trades) / len(trades) if trades else 0.0

        return OcrResponse(
            raw_text=original_text,
            trades=trades,
            confidence=avg_confidence,
            warnings=warnings,
            broker_detected=result.get("broker_detected"),
            original_date_format=result.get("original_date_format"),
        )

    @staticmethod
    def extract_trade_lines(text: str) -> list[str]:
        """Extract broker statement rows that look like trade records."""
        lines: list[str] = []
        for raw_line in text.splitlines():
            line = " ".join(raw_line.strip().split())
            if not line:
                continue
            if TRADE_LINE_PATTERN.match(line):
                lines.append(line)
        return lines

    @classmethod
    def parse_broker_statement_table_fallback(cls, text: str) -> tuple[list[ParsedTrade], list[str]]:
        """Parse broker transaction tables and validate them against statement totals."""
        detail_text = cls.extract_trade_detail_section(text)
        totals = cls.extract_statement_totals(text)
        candidates = [
            cls.parse_markdown_trade_table_fallback(detail_text),
            cls.parse_vertical_trade_blocks_fallback(detail_text),
        ]
        candidates = [candidate for candidate in candidates if candidate]
        if not candidates:
            return [], []

        best_trades = candidates[0]
        best_warnings = cls.validate_trade_totals(best_trades, totals) if totals else []
        for candidate in candidates[1:]:
            candidate_warnings = cls.validate_trade_totals(candidate, totals) if totals else []
            if totals and not candidate_warnings:
                best_trades = candidate
                best_warnings = candidate_warnings
                break
            if len(candidate) > len(best_trades) and (not totals or len(candidate_warnings) <= len(best_warnings)):
                best_trades = candidate
                best_warnings = candidate_warnings

        warnings = ["Broker statement table fallback was used; please review before confirming."]
        if totals:
            warnings.extend(best_warnings)
            if not best_warnings:
                warnings.append("Statement totals matched parsed trades.")

        return best_trades, warnings

    @staticmethod
    def extract_trade_detail_section(text: str) -> str:
        """Limit parsing to the transaction detail section and ignore holdings/notice text."""
        start_match = re.search(r"上市[、,/]?上櫃[、,/]?興櫃交易明細", text)
        start = start_match.end() if start_match else 0

        end_match = re.search(r"上市/櫃\s*\(TWD\)\s*合計", text[start:])
        if end_match:
            return text[start : start + end_match.start()]
        return text[start:]

    @classmethod
    def parse_markdown_trade_table_fallback(cls, text: str) -> list[ParsedTrade]:
        """Parse OCR pipe tables where one trade spans trade and settlement rows."""
        rows: list[list[str]] = []
        for raw_line in text.splitlines():
            cells = cls._split_pipe_table_row(raw_line)
            if not cells or cls._is_markdown_separator_row(cells):
                continue
            rows.append(cells)

        trades: list[ParsedTrade] = []
        index = 0
        while index < len(rows):
            cells = rows[index]
            parsed = cls._parse_markdown_trade_base_row(cells)
            if parsed is None:
                index += 1
                continue

            settlement_date = None
            stock_name = parsed["stock_name"]
            if index + 1 < len(rows):
                detail_cells = rows[index + 1]
                if cls._is_markdown_trade_detail_row(detail_cells):
                    settlement_date = cls.parse_roc_date(cls._cell(detail_cells, 0))
                    detail_name = cls._cell(detail_cells, 4)
                    if detail_name:
                        stock_name = detail_name
                    index += 1

            warnings = ["Markdown table fallback parse; please review before confirming."]
            side = parsed["side"]
            tax = parsed["tax"]
            if side == TradeSide.BUY and tax is not None:
                side = TradeSide.SELL
                warnings.append("Side inferred as SELL from transaction tax.")

            try:
                trades.append(
                    ParsedTrade(
                        ticker=parsed["ticker"],
                        side=side,
                        quantity=parsed["quantity"],
                        price=parsed["price"],
                        trade_date=parsed["trade_date"],
                        settlement_date=settlement_date,
                        currency=parsed["currency"],
                        fee=parsed["fee"],
                        tax=tax,
                        stock_name=stock_name,
                        confidence=0.88,
                        warnings=warnings,
                        raw_line=" | ".join(cells),
                    )
                )
            except Exception as e:
                logger.warning("Markdown table fallback failed", error=str(e), raw_row=cells)
            index += 1

        return trades

    @classmethod
    def parse_vertical_trade_blocks_fallback(cls, text: str) -> list[ParsedTrade]:
        """Parse PDF text-layer rows where each trade is emitted as a vertical block."""
        lines = cls._normalize_vertical_lines(text)
        trades: list[ParsedTrade] = []
        index = 0
        while index < len(lines) - 5:
            trade_date = cls.parse_roc_date(lines[index])
            settlement_date = cls.parse_roc_date(lines[index + 1])
            market_line = lines[index + 2]
            market_match = re.fullmatch(
                r"(?P<market>集中|櫃檯|權證|興櫃)?\s*(?P<currency>TWD|USD)\s*(?P<side>買進|賣出|買|賣|BUY|SELL)",
                market_line,
                re.IGNORECASE,
            )
            if not trade_date or not settlement_date or not market_match:
                index += 1
                continue

            ticker = lines[index + 3]
            stock_name = lines[index + 4]
            amount_line = lines[index + 5]
            amount_match = re.fullmatch(
                r"(?P<quantity>[\d,]+(?:\.0+)?)\s+"
                r"(?P<price>[\d,]+(?:\.\d+)?)\s+"
                r"(?P<amount>[\d,]+(?:\.\d+)?)\s+"
                r"(?P<fee>[\d,]+(?:\.\d+)?)"
                r"(?:\s+(?P<tax>[\d,]+(?:\.\d+)?))?",
                amount_line,
            )
            if not re.fullmatch(r"[A-Z0-9][A-Z0-9.-]{1,12}", ticker, re.IGNORECASE) or not amount_match:
                index += 1
                continue

            side = cls._parse_trade_side(market_match.group("side"))
            quantity = cls._parse_int(amount_match.group("quantity"))
            price = cls._parse_decimal(amount_match.group("price"))
            fee = cls._parse_decimal(amount_match.group("fee"))
            tax = cls._parse_decimal(amount_match.group("tax") or "")
            currency = market_match.group("currency").upper()
            if side is None or quantity is None or price is None:
                index += 1
                continue

            try:
                trades.append(
                    ParsedTrade(
                        ticker=ticker,
                        side=side,
                        quantity=quantity,
                        price=price,
                        trade_date=trade_date,
                        settlement_date=settlement_date,
                        currency=Currency(currency),
                        fee=fee,
                        tax=tax,
                        stock_name=stock_name,
                        confidence=0.9,
                        warnings=["Vertical text-layer fallback parse; please review before confirming."],
                        raw_line=" | ".join(lines[index : index + 6]),
                    )
                )
                index += 6
            except Exception as e:
                logger.warning("Vertical trade fallback failed", error=str(e), raw_lines=lines[index : index + 6])
                index += 1
        return trades

    @staticmethod
    def _normalize_vertical_lines(text: str) -> list[str]:
        lines: list[str] = []
        for raw_line in text.splitlines():
            line = raw_line.replace("\u3000", " ").strip()
            line = " ".join(line.split())
            line = re.sub(r"(集中|櫃檯|權證|興櫃)\s*(TWD|USD)\s*(買進|賣出|買|賣|BUY|SELL)", r"\1 \2 \3", line, flags=re.IGNORECASE)
            if not line:
                continue
            if set(line) <= {"-", "─"}:
                continue
            lines.append(line)
        return lines

    @staticmethod
    def _split_pipe_table_row(raw_line: str) -> list[str] | None:
        line = raw_line.strip()
        if "|" not in line:
            return None
        if line.count("|") < 3:
            return None
        return [cell.strip() for cell in line.strip("|").split("|")]

    @staticmethod
    def _is_markdown_separator_row(cells: list[str]) -> bool:
        non_empty = [cell.strip() for cell in cells if cell.strip()]
        return bool(non_empty) and all(re.fullmatch(r":?-{3,}:?", cell) for cell in non_empty)

    @classmethod
    def _parse_markdown_trade_base_row(cls, cells: list[str]) -> dict[str, object] | None:
        trade_date = cls.parse_roc_date(cls._cell(cells, 0))
        side = cls._parse_trade_side(cls._cell(cells, 3))
        quantity = cls._parse_int(cls._cell(cells, 5))
        price = cls._parse_decimal(cls._cell(cells, 6))
        currency_value = cls._cell(cells, 2).upper()
        ticker, stock_name = cls._split_security_cell(cls._cell(cells, 4))

        if (
            trade_date is None
            or side is None
            or quantity is None
            or price is None
            or not ticker
            or currency_value not in {Currency.TWD.value, Currency.USD.value}
        ):
            return None

        return {
            "ticker": ticker,
            "stock_name": stock_name,
            "side": side,
            "quantity": quantity,
            "price": price,
            "trade_date": trade_date,
            "currency": Currency(currency_value),
            "fee": cls._parse_decimal(cls._cell(cells, 8)),
            "tax": cls._parse_decimal(cls._cell(cells, 9)),
            "net_amount": cls._parse_signed_decimal(cls._cell(cells, 11)),
        }

    @classmethod
    def _is_markdown_trade_detail_row(cls, cells: list[str]) -> bool:
        if cls.parse_roc_date(cls._cell(cells, 0)) is None:
            return False
        if cls._parse_trade_side(cls._cell(cells, 3)) is not None:
            return False
        if cls._parse_int(cls._cell(cells, 5)) is not None:
            return False
        return bool(cls._cell(cells, 4))

    @staticmethod
    def _split_security_cell(value: str) -> tuple[str | None, str | None]:
        security = value.strip()
        if not security:
            return None, None

        parts = security.split(maxsplit=1)
        ticker = parts[0]
        if re.fullmatch(r"[A-Z0-9][A-Z0-9.-]{1,12}", ticker, re.IGNORECASE):
            return ticker, parts[1].strip() if len(parts) > 1 else None
        return None, security

    @staticmethod
    def _cell(cells: list[str], index: int) -> str:
        return cells[index].strip() if index < len(cells) else ""

    @classmethod
    def build_trade_parser_text(cls, text: str, trade_lines: list[str] | None = None) -> str:
        """Build parser input without dropping imperfect but trade-relevant rows."""
        relevant_lines = cls.extract_trade_relevant_lines(text)
        if relevant_lines:
            return "\n".join(relevant_lines)
        if trade_lines:
            return "\n".join(trade_lines)
        return text

    @classmethod
    def extract_trade_relevant_lines(cls, text: str) -> list[str]:
        """Extract table headers, exact trade rows, and likely trade rows for the LLM parser."""
        lines: list[str] = []
        seen: set[str] = set()
        for raw_line in text.splitlines():
            line = " ".join(raw_line.strip().split())
            if not line or line in seen:
                continue
            if cls._is_trade_relevant_line(line):
                lines.append(line)
                seen.add(line)
        return lines

    @classmethod
    def _is_trade_relevant_line(cls, line: str) -> bool:
        if TRADE_LINE_PATTERN.match(line):
            return True
        if cls._has_trade_page_signal(line):
            return True
        keyword_hits = sum(1 for keyword in TRADE_PAGE_KEYWORDS if keyword in line)
        if keyword_hits >= 2:
            return True
        has_date = bool(re.search(r"\d{2,4}/\d{1,2}/\d{1,2}", line))
        has_side = bool(re.search(r"(?:買進|賣出|買|賣|BUY|SELL)", line, re.IGNORECASE))
        has_currency = bool(re.search(r"\b(?:TWD|USD)\b", line, re.IGNORECASE))
        has_ticker = bool(re.search(r"(?:^|\s)[A-Z0-9][A-Z0-9.-]{1,12}(?:\s|$)", line))
        return has_date and (has_side or has_currency or has_ticker)

    @classmethod
    def classify_pdf_page_text(cls, page_text: str | None) -> tuple[str, str]:
        """Classify a PDF page before deciding whether Vision OCR is worth calling."""
        text = (page_text or "").strip()
        if not text:
            return PDF_PAGE_USE_VISION, "no_text_layer"

        if cls.extract_trade_lines(text):
            return PDF_PAGE_USE_TEXT_AND_VISION, "trade_lines_in_text_layer"

        if cls._has_trade_page_signal(text):
            return PDF_PAGE_USE_TEXT_AND_VISION, "trade_page_signal"

        if cls._has_non_trade_page_signal(text):
            return PDF_PAGE_SKIP, "non_trade_page_signal"

        return PDF_PAGE_USE_VISION, "uncertain_page"

    @staticmethod
    def _has_trade_page_signal(text: str) -> bool:
        compact = " ".join(text.split())
        if TRADE_SIGNAL_PATTERN.search(compact):
            return True

        keyword_hits = sum(1 for keyword in TRADE_PAGE_KEYWORDS if keyword in compact)
        if keyword_hits >= 2:
            return True

        return bool(
            re.search(
                r"\d{2,4}/\d{1,2}/\d{1,2}.*(?:TWD|USD).*(?:買進|賣出|買|賣|BUY|SELL)",
                compact,
                re.IGNORECASE,
            )
        )

    @staticmethod
    def _has_non_trade_page_signal(text: str) -> bool:
        compact = " ".join(text.split())
        if "帳戶總覽" in compact or "資產總覽" in compact:
            return True
        if "重要聲明" in compact or "風險預告" in compact:
            return True
        if "注意事項" in compact and "交易明細" not in compact:
            return True
        return "現值折合台幣" in compact and "比重" in compact

    @classmethod
    def parse_trade_lines_fallback(cls, lines: list[str]) -> list[ParsedTrade]:
        """Parse common TW broker monthly-statement trade rows without an LLM."""
        trades: list[ParsedTrade] = []
        for line in lines:
            match = TRADE_LINE_PATTERN.match(line)
            if not match:
                continue

            trade_date = cls.parse_roc_date(match.group("trade_date"))
            settlement_date = cls.parse_roc_date(match.group("settlement_date"))
            side = cls._parse_trade_side(match.group("side"))
            quantity = cls._parse_int(match.group("quantity"))
            price = cls._parse_decimal(match.group("price"))
            currency = match.group("currency").upper()
            if not trade_date or not side or quantity is None or price is None:
                logger.warning("Rule-based trade fallback skipped invalid line", raw_line=line)
                continue

            fee, tax = cls._parse_fee_tax(match.group("rest") or "")
            try:
                trades.append(
                    ParsedTrade(
                        ticker=match.group("ticker"),
                        side=side,
                        quantity=quantity,
                        price=price,
                        trade_date=trade_date,
                        settlement_date=settlement_date,
                        currency=Currency(currency),
                        fee=fee,
                        tax=tax,
                        stock_name=match.group("stock_name").strip(),
                        confidence=0.85,
                        warnings=["Rule-based fallback parse; please review before confirming."],
                        raw_line=line,
                    )
                )
            except Exception as e:
                logger.warning("Rule-based trade fallback failed", error=str(e), raw_line=line)
        return trades

    @staticmethod
    def _parse_trade_side(side: str) -> TradeSide | None:
        normalized = side.strip().upper()
        if normalized in {"買", "買進", "BUY"}:
            return TradeSide.BUY
        if normalized in {"賣", "賣出", "SELL"}:
            return TradeSide.SELL
        return None

    @staticmethod
    def _parse_int(value: str) -> int | None:
        try:
            return int(Decimal(value.replace(",", "")))
        except Exception:
            return None

    @staticmethod
    def _parse_decimal(value: str) -> Decimal | None:
        try:
            cleaned = value.strip().replace(",", "").replace("(", "").replace(")", "")
            if not cleaned:
                return None
            return Decimal(cleaned)
        except Exception:
            return None

    @staticmethod
    def _parse_signed_decimal(value: str) -> Decimal | None:
        try:
            raw = value.strip()
            if not raw:
                return None
            negative = raw.startswith("(") and raw.endswith(")")
            cleaned = raw.replace(",", "").replace("(", "").replace(")", "")
            if not cleaned:
                return None
            amount = Decimal(cleaned)
            return -amount if negative else amount
        except Exception:
            return None

    @classmethod
    def _parse_fee_tax(cls, rest: str) -> tuple[Decimal | None, Decimal | None]:
        tokens = AMOUNT_TOKEN_PATTERN.findall(rest)
        if len(tokens) < 2:
            return None, None
        fee = cls._parse_decimal(tokens[1])
        tax = cls._parse_decimal(tokens[2]) if len(tokens) >= 4 else None
        return fee, tax

    @classmethod
    def extract_statement_totals(cls, text: str) -> dict[str, Decimal] | None:
        match = re.search(r"上市/櫃\s*\(TWD\)\s*合計[^\n]*", text)
        if not match:
            return None

        line = match.group(0)
        totals: dict[str, Decimal] = {}
        amount = re.search(r"成交金額[:：]\s*\(?([\d,]+(?:\.\d+)?)\)?", line)
        fee = re.search(r"手續費[:：]\s*\(?([\d,]+(?:\.\d+)?)\)?", line)
        tax = re.search(r"代繳交易稅[:：]\s*\(?([\d,]+(?:\.\d+)?)\)?", line)
        if amount:
            totals["amount"] = Decimal(amount.group(1).replace(",", ""))
        if fee:
            totals["fee"] = Decimal(fee.group(1).replace(",", ""))
        if tax:
            totals["tax"] = Decimal(tax.group(1).replace(",", ""))
        return totals or None

    @classmethod
    def validate_trade_totals(cls, trades: list[ParsedTrade], totals: dict[str, Decimal]) -> list[str]:
        warnings: list[str] = []
        if "amount" in totals:
            actual_amount = sum(trade.price * Decimal(trade.quantity) for trade in trades)
            if actual_amount != totals["amount"]:
                warnings.append(
                    f"Parsed amount total {actual_amount} does not match statement total {totals['amount']}."
                )
        if "fee" in totals:
            actual_fee = sum((trade.fee or Decimal("0")) for trade in trades)
            if actual_fee != totals["fee"]:
                warnings.append(
                    f"Parsed fee total {actual_fee} does not match statement total {totals['fee']}."
                )
        if "tax" in totals:
            actual_tax = sum((trade.tax or Decimal("0")) for trade in trades)
            if actual_tax != totals["tax"]:
                warnings.append(
                    f"Parsed tax total {actual_tax} does not match statement total {totals['tax']}."
                )
        return warnings

    @staticmethod
    def detect_broker(text: str) -> str | None:
        if "元大證券" in text or "Yuanta" in text:
            return "元大證券"
        return None

    @staticmethod
    def convert_roc_to_ad(roc_year: int) -> int:
        """Convert ROC (民國) year to AD (西元) year."""
        return roc_year + 1911

    @staticmethod
    def parse_roc_date(date_str: str) -> date | None:
        """Parse a date string that might be in ROC format."""
        date_str = date_str.strip()

        # Try ROC/AD slash format: YYY/MM/DD or YYYY/MM/DD
        match = re.match(r"^(\d{2,4})/(\d{1,2})/(\d{1,2})$", date_str)
        if match:
            year = int(match.group(1))
            month = int(match.group(2))
            day = int(match.group(3))
            if year < 1000:  # ROC year
                year = OcrService.convert_roc_to_ad(year)
            return date(year, month, day)

        # Try ROC format: YYYMMDD (7 digits)
        if len(date_str) == 7 and date_str.isdigit():
            year = OcrService.convert_roc_to_ad(int(date_str[:3]))
            month = int(date_str[3:5])
            day = int(date_str[5:7])
            return date(year, month, day)

        # Try AD format: YYYYMMDD (8 digits)
        if len(date_str) == 8 and date_str.isdigit():
            year = int(date_str[:4])
            month = int(date_str[4:6])
            day = int(date_str[6:8])
            return date(year, month, day)

        return None
