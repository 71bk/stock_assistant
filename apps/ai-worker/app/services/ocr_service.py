"""OCR Service - handles image/PDF processing and trade extraction."""

import base64
import io
import json
import re
from datetime import date
from decimal import Decimal
from enum import Enum

import structlog
from PIL import Image

from app.config import LlmProvider, OcrProvider, get_settings
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

logger = structlog.get_logger()


class OcrMethod(str, Enum):
    """OCR method used for extraction."""
    TESSERACT = "tesseract"
    VISION_LLM = "vision_llm"


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

        # Handle PDF: convert to image first
        image_bytes = content
        if request.content_type == "application/pdf":
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
                logger.info("Converting PDF to images")
                # 指定 Poppler 路徑（Windows 需要明確指定）
                poppler_path = r"C:\poppler\poppler-25.12.0\Library\bin"
                images = convert_from_bytes(content, poppler_path=poppler_path)
                if not images:
                    raise ValueError("No pages extracted from PDF")
                
                # Handle multiple pages: stitch all pages vertically into one long image
                if len(images) == 1:
                    merged_image = images[0]
                else:
                    # Calculate total height and max width
                    total_height = sum(img.height for img in images)
                    max_width = max(img.width for img in images)
                    
                    # Create merged image
                    merged_image = Image.new("RGB", (max_width, total_height), "white")
                    y_offset = 0
                    for img in images:
                        merged_image.paste(img, (0, y_offset))
                        y_offset += img.height
                    
                    logger.info("PDF pages merged", pages=len(images), total_height=total_height)
                
                img_buffer = io.BytesIO()
                merged_image.save(img_buffer, format="PNG")
                image_bytes = img_buffer.getvalue()
                logger.info("PDF converted to image", pages=len(images))
            except Exception as e:
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
        if use_tesseract and self.tesseract.is_available():
            try:
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
                    raw_text = ""  # Reset to trigger fallback

            except Exception as e:
                logger.warning("Tesseract extraction failed", error=str(e))
                raw_text = ""

        # Path B: Vision LLM (AUTO fallback or explicit provider)
        if not raw_text and allow_vision:
            if self.llm is None:
                raise RuntimeError("Vision provider not configured")
            logger.info("Using Vision LLM for OCR", provider=self.vision_provider.value)
            ocr_method = OcrMethod.VISION_LLM

            # Encode image to base64
            image_base64 = base64.b64encode(image_bytes).decode("utf-8")

            # Map content type (PDF 已轉換成 PNG)
            media_type = request.content_type
            if media_type == "application/pdf":
                media_type = "image/png"  # PDF 已轉換成 PNG 圖片
            elif media_type == "image/jpg":
                media_type = "image/jpeg"

            # Extract text using Vision LLM
            raw_text = await self.llm.vision(
                image_base64=image_base64,
                prompt=VISION_EXTRACT_PROMPT,
                media_type=media_type,
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
        max_text_length = 8000  # 約 2600 tokens，保守設定以確保輸出空間充足
        if len(text) > max_text_length:
            logger.warning("Text too long, truncating", original_length=len(text), max_length=max_text_length)
            text = text[:max_text_length] + "\n\n[文字已截斷...]"

        broker_hint = f"券商: {broker}" if broker else "券商未知"

        messages = [
            {"role": "system", "content": get_parse_prompt(broker_hint)},
            {"role": "user", "content": f"請解析以下交易資料：\n\n{text}"},
        ]

        response_text = await self.llm.chat(messages, temperature=0.1, max_tokens=8192, json_mode=True)

        # Parse LLM response
        try:
            # Try to extract JSON from response (in case there's extra text)
            json_match = re.search(r'\{[\s\S]*\}', response_text)
            if json_match:
                result = json.loads(json_match.group())
            else:
                result = json.loads(response_text)
        except json.JSONDecodeError as e:
            logger.error("Failed to parse LLM response as JSON", error=str(e))
            return OcrResponse(
                raw_text=text,
                trades=[],
                confidence=0.0,
                warnings=[f"Failed to parse LLM response: {str(e)}"],
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

        # Calculate overall confidence
        avg_confidence = sum(t.confidence for t in trades) / len(trades) if trades else 0.0

        return OcrResponse(
            raw_text=text,
            trades=trades,
            confidence=avg_confidence,
            warnings=result.get("warnings", []),
            broker_detected=result.get("broker_detected"),
            original_date_format=result.get("original_date_format"),
        )

    @staticmethod
    def convert_roc_to_ad(roc_year: int) -> int:
        """Convert ROC (民國) year to AD (西元) year."""
        return roc_year + 1911

    @staticmethod
    def parse_roc_date(date_str: str) -> date | None:
        """Parse a date string that might be in ROC format."""
        date_str = date_str.strip()

        # Try ROC format: YYY/MM/DD
        match = re.match(r"^(\d{2,3})/(\d{1,2})/(\d{1,2})$", date_str)
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
