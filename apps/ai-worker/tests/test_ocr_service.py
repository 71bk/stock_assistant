"""Tests for OCR service."""

from datetime import date
from decimal import Decimal

import pytest
from PIL import Image

from app.config import OcrProvider
from app.models.schemas import OcrRequest
from app.services import ocr_service as ocr_module
from app.services.ocr_service import PdfPasswordInvalidError, PdfPasswordRequiredError
from app.services.ocr_service import OcrService


class TestRocDateConversion:
    """Tests for ROC to AD date conversion."""

    def test_convert_roc_to_ad(self):
        """Test basic ROC year conversion."""
        assert OcrService.convert_roc_to_ad(113) == 2024
        assert OcrService.convert_roc_to_ad(112) == 2023
        assert OcrService.convert_roc_to_ad(100) == 2011
        assert OcrService.convert_roc_to_ad(1) == 1912

    def test_parse_roc_date_slash_format(self):
        """Test parsing ROC date with slashes."""
        result = OcrService.parse_roc_date("113/01/15")
        assert result == date(2024, 1, 15)

        result = OcrService.parse_roc_date("112/12/31")
        assert result == date(2023, 12, 31)

    def test_parse_roc_date_compact_format(self):
        """Test parsing ROC date in compact format (7 digits)."""
        result = OcrService.parse_roc_date("1130115")
        assert result == date(2024, 1, 15)

        result = OcrService.parse_roc_date("1121231")
        assert result == date(2023, 12, 31)

    def test_parse_ad_date_compact_format(self):
        """Test parsing AD date in compact format (8 digits)."""
        result = OcrService.parse_roc_date("20240115")
        assert result == date(2024, 1, 15)

        result = OcrService.parse_roc_date("20231231")
        assert result == date(2023, 12, 31)

    def test_parse_ad_date_slash_format(self):
        """Test parsing AD date with slashes."""
        result = OcrService.parse_roc_date("2025/11/03")
        assert result == date(2025, 11, 3)

    def test_parse_invalid_date(self):
        """Test parsing invalid date strings."""
        assert OcrService.parse_roc_date("invalid") is None
        assert OcrService.parse_roc_date("") is None
        assert OcrService.parse_roc_date("12345") is None


class TestPdfPasswordHandling:
    """Tests for password protected PDF handling."""

    @pytest.mark.asyncio
    async def test_process_pdf_without_password_raises_required(self, monkeypatch):
        """Encrypted PDF without password should ask the caller for a password."""
        monkeypatch.setattr(ocr_module, "PDF_SUPPORT", True)
        monkeypatch.setattr(
            ocr_module,
            "convert_from_bytes",
            lambda *args, **kwargs: (_ for _ in ()).throw(RuntimeError("Incorrect password")),
        )

        request = OcrRequest(
            user_id="7",
            filename="statement.pdf",
            content_type="application/pdf",
        )

        with pytest.raises(PdfPasswordRequiredError):
            await OcrService().process(b"%PDF", request)

    @pytest.mark.asyncio
    async def test_process_pdf_with_bad_password_raises_invalid(self, monkeypatch):
        """Encrypted PDF with a rejected password should be reported as invalid."""
        monkeypatch.setattr(ocr_module, "PDF_SUPPORT", True)
        monkeypatch.setattr(
            ocr_module,
            "convert_from_bytes",
            lambda *args, **kwargs: (_ for _ in ()).throw(RuntimeError("Incorrect password")),
        )

        request = OcrRequest(
            user_id="7",
            filename="statement.pdf",
            content_type="application/pdf",
            pdf_password="bad-secret",
        )

        with pytest.raises(PdfPasswordInvalidError):
            await OcrService().process(b"%PDF", request)


class TestTradeLineFallback:
    """Tests for rule-based TW broker trade parsing fallback."""

    SAMPLE_TEXT = """
元大證券 Yuanta Securities
電子綜合月對帳單
帳戶總覽
資產總淨額 793,388

2025/11/03      2025/11/05      集中    TWD     買      2308    台達電  10      988.0000        9,880   8                       (9,888)
2025/11/03      2025/11/05      集中    TWD     買      2330    台積電  6       1,500.0000      9,000   7                       (9,007)
2025/11/07      2025/11/11      權證    TWD     賣      6290    良維    100     194.0000        19,400  16      58              19,326
"""

    def test_extract_trade_lines_should_ignore_statement_summary(self):
        lines = OcrService.extract_trade_lines(self.SAMPLE_TEXT)

        assert len(lines) == 3
        assert lines[0].startswith("2025/11/03 2025/11/05 集中 TWD 買 2308")
        assert "帳戶總覽" not in "\n".join(lines)

    def test_parse_trade_lines_fallback_should_parse_tw_statement_rows(self):
        lines = OcrService.extract_trade_lines(self.SAMPLE_TEXT)

        trades = OcrService.parse_trade_lines_fallback(lines)

        assert len(trades) == 3
        assert trades[0].ticker == "2308"
        assert trades[0].stock_name == "台達電"
        assert trades[0].side.value == "BUY"
        assert trades[0].quantity == 10
        assert trades[0].price == Decimal("988.0000")
        assert trades[0].fee == Decimal("8")
        assert trades[0].tax is None
        assert trades[0].trade_date == date(2025, 11, 3)
        assert trades[0].settlement_date == date(2025, 11, 5)
        assert trades[2].side.value == "SELL"
        assert trades[2].tax == Decimal("58")

    @pytest.mark.asyncio
    async def test_parse_text_should_fallback_when_llm_returns_no_trades(self):
        class FakeLlm:
            async def chat(self, *args, **kwargs):
                return '{"trades": [], "broker_detected": "元大證券", "warnings": ["no trades found"]}'

        service = OcrService()
        service.llm = FakeLlm()

        response = await service.parse_text(self.SAMPLE_TEXT, user_id="1")

        assert len(response.trades) == 3
        assert response.raw_text == self.SAMPLE_TEXT
        assert response.broker_detected == "元大證券"
        assert any("rule-based" in warning for warning in response.warnings)


class TestMarkdownTradeTableFallback:
    """Tests for Yuanta-style Markdown trade tables returned by Vision OCR."""

    SAMPLE_TABLE = """
帳號：989U-01**63
上市、上櫃、興櫃交易明細

| 成交日期/交割日期 | 交易類別 | 幣別 | 買賣 | 證券名稱 | 股數 | 單價 | 成交金額 | 手續費 | 代繳交易稅 | 借息 利息累計稅款 | 客戶淨收(客戶淨付) |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 2025/11/03 | 集中 | TWD | 買 | 2308 | 10 | 988.0000 | 9,880 | 8 | | | (9,888) |
| 2025/11/05 | | | | 台達電 | | | | | | | |
| 2025/11/07 | 櫃檯 | TWD | 買 | 6290 | 100 | 194.0000 | 19,400 | 16 | 58 | 19,326 | |
| 2025/11/11 | | | | 良維 | | | | | | | |
| 2025/11/18 | 集中 | TWD | 買 | 00965 | 500 | 22.4000 | 11,200 | 9 | | | (11,209) |
| 2025/11/20 | | | | 元大航太防衛科技 | | | | | | | |

上市/櫃(TWD)合計 成交金額：40,480 手續費：33 代繳交易稅：58
"""

    PIPE_TABLE = """
帳號：989U-01**63
上市、上櫃、興櫃交易明細

成交日期     | 交易 | 幣別 | 買賣 | 證券名稱        | 股數 | 單價      | 成交金額 | 手續費 | 代繳 | 消息                 | 融資/融券
交割日期     | 類別 |     |     |                 |      |           |          |       | 交易稅 | 利息累計稅款 | 客戶淨收(客戶淨付) | 自備款擔保款
-------------|------|-----|-----|-----------------|------|-----------|----------|-------|-------|-------------|--------------------
2025/11/03   | 集中 | TWD | 買  | 2308            | 10   | 988.0000  | 9,880    | 8     |       |             | (9,888)
2025/11/05   |      |     |     | 台達電          |      |           |          |       |       |             |
2025/11/03   | 集中 | TWD | 買  | 2317            | 50   | 251.0000  | 12,550   | 10    |       |             | (12,560)
2025/11/05   |      |     |     | 鴻海            |      |           |          |       |       |             |
2025/11/07   | 櫃檯 | TWD | 買  | 6290            | 100  | 194.0000  | 19,400   | 16    | 58    | 19,326      |
2025/11/11   |      |     |     | 良維            |      |           |          |       |       |             |

上市/櫃(TWD)合計 成交金額：41,830 手續費：34 代繳交易稅：58

證券代號 證券名稱 | 集保庫存 | 融資庫存 | 融券庫存 | 抵繳品 (保證品) | 參考價 | 融資金額 | 融券保證金 | 融券擔保品 | 融券市值 | 市值
3689 湧德 | 300 | | | | 104.5000 | | | | | 31,350
"""

    VERTICAL_TEXT = """
帳號：989U-01**63
上市、上櫃、興櫃交易明細
成交日期
交割日期
交易
類別 幣別 買賣 證券名稱 股數 單價 成交金額 手續費 代繳
2025/11/03
2025/11/05
集中TWD買
2308
台達電
10 988.0000 9,880 8
　
　
(9,888)
2025/11/07
2025/11/11
櫃檯TWD賣
6290
良維
100 194.0000 19,400 16 58
　
　
19,326
2025/11/18
2025/11/20
集中TWD買
00965
元大航太防衛科技
500 22.4000 11,200 9
　
　
(11,209)
2025/11/28
2025/12/02
櫃檯TWD買
737362
良維群益53購03
1,000 7.0000 7,000 20
上市/櫃(TWD)合計 成交金額：47,480 手續費：53 代繳交易稅：58

上市、上櫃、興櫃庫存明細(集保、信用)(不含已下市櫃股票)
3689
湧德
300 104.5000 31,350
"""

    def test_parse_markdown_trade_table_fallback(self):
        trades = OcrService.parse_markdown_trade_table_fallback(self.SAMPLE_TABLE)

        assert len(trades) == 3
        assert trades[0].ticker == "2308"
        assert trades[0].stock_name == "台達電"
        assert trades[0].quantity == 10
        assert trades[0].price == Decimal("988.0000")
        assert trades[0].fee == Decimal("8")
        assert trades[0].trade_date == date(2025, 11, 3)
        assert trades[0].settlement_date == date(2025, 11, 5)
        assert trades[0].side.value == "BUY"

        assert trades[1].ticker == "6290"
        assert trades[1].stock_name == "良維"
        assert trades[1].tax == Decimal("58")
        assert trades[1].side.value == "SELL"
        assert any("Side inferred" in warning for warning in trades[1].warnings)

        assert trades[2].ticker == "00965"
        assert trades[2].stock_name == "元大航太防衛科技"

    def test_parse_pipe_table_without_outer_pipes(self):
        trades, warnings = OcrService.parse_broker_statement_table_fallback(self.PIPE_TABLE)

        assert len(trades) == 3
        assert trades[0].ticker == "2308"
        assert trades[0].stock_name == "台達電"
        assert trades[1].ticker == "2317"
        assert trades[1].stock_name == "鴻海"
        assert trades[2].ticker == "6290"
        assert trades[2].stock_name == "良維"
        assert trades[2].side.value == "SELL"
        assert any("Statement totals matched" in warning for warning in warnings)
        assert "3689" not in {trade.ticker for trade in trades}

    def test_parse_vertical_text_layer_blocks(self):
        trades, warnings = OcrService.parse_broker_statement_table_fallback(self.VERTICAL_TEXT)

        assert len(trades) == 4
        assert trades[0].ticker == "2308"
        assert trades[0].stock_name == "台達電"
        assert trades[0].quantity == 10
        assert trades[0].price == Decimal("988.0000")
        assert trades[1].ticker == "6290"
        assert trades[1].side.value == "SELL"
        assert trades[1].tax == Decimal("58")
        assert trades[2].ticker == "00965"
        assert trades[2].stock_name == "元大航太防衛科技"
        assert trades[3].ticker == "737362"
        assert trades[3].quantity == 1000
        assert trades[3].fee == Decimal("20")
        assert any("Statement totals matched" in warning for warning in warnings)

    @pytest.mark.asyncio
    async def test_parse_text_should_use_markdown_fallback_before_llm(self):
        class FakeLlm:
            async def chat(self, *args, **kwargs):
                raise AssertionError("LLM parser should not be called for markdown trade tables")

        service = OcrService()
        service.llm = FakeLlm()

        response = await service.parse_text(self.SAMPLE_TABLE, user_id="1")

        assert len(response.trades) == 3
        assert response.confidence > 0
        assert response.raw_text == self.SAMPLE_TABLE
        assert any("Broker statement" in warning for warning in response.warnings)


class TestPdfPageFiltering:
    """Tests for conservative PDF page filtering before Vision OCR."""

    SUMMARY_PAGE = """
元大證券 Yuanta Securities
電子綜合月對帳單
帳戶總覽
現值折合台幣 793,388
比重 100.00%
客服專線電話: (02)2718-5886
"""

    TRADE_ROW_PAGE = """
成交日期 交割日期 市場 幣別 買賣 股票代號 商品名稱 數量 成交價
2025/11/03 2025/11/05 集中 TWD 買 2308 台達電 10 988.0000 9,880 8 (9,888)
"""

    TRADE_HEADER_PAGE = """
成交日期 交割日期 市場 幣別 買賣 股票代號 商品名稱 數量 成交價 手續費 交易稅 淨收付
"""

    def test_classify_summary_page_should_skip(self):
        action, reason = OcrService.classify_pdf_page_text(self.SUMMARY_PAGE)

        assert action == ocr_module.PDF_PAGE_SKIP
        assert reason == "non_trade_page_signal"

    def test_classify_trade_row_page_should_use_text_layer(self):
        action, reason = OcrService.classify_pdf_page_text(self.TRADE_ROW_PAGE)

        assert action == ocr_module.PDF_PAGE_USE_TEXT_AND_VISION
        assert reason == "trade_lines_in_text_layer"

    def test_build_trade_parser_text_should_keep_imperfect_trade_rows(self):
        text = """
元大證券 Yuanta Securities
帳戶總覽
成交日期 交割日期 市場 幣別 買賣 股票代號 商品名稱 數量 成交價
2025/11/03 2025/11/05 集中 TWD 買 2308 台達電 10 988.0000 9,880 8 (9,888)
2025/11/04 2025/11/06 集中 TWD 買 2317
鴻海 5 210.0000 1,050 1 (1,051)
現值折合台幣 793,388
"""

        parser_text = OcrService.build_trade_parser_text(text, OcrService.extract_trade_lines(text))

        assert "帳戶總覽" not in parser_text
        assert "成交日期" in parser_text
        assert "2025/11/04 2025/11/06 集中 TWD 買 2317" in parser_text
        assert "現值折合台幣" not in parser_text

    def test_classify_trade_header_page_should_use_vision(self):
        action, reason = OcrService.classify_pdf_page_text(self.TRADE_HEADER_PAGE)

        assert action == ocr_module.PDF_PAGE_USE_TEXT_AND_VISION
        assert reason == "trade_page_signal"

    def test_classify_empty_page_should_use_vision(self):
        action, reason = OcrService.classify_pdf_page_text("")

        assert action == ocr_module.PDF_PAGE_USE_VISION
        assert reason == "no_text_layer"

    def test_classify_generic_statement_header_should_not_skip(self):
        action, reason = OcrService.classify_pdf_page_text("元大證券 客服專線電話: (02)2718-5886")

        assert action == ocr_module.PDF_PAGE_USE_VISION
        assert reason == "uncertain_page"

    @pytest.mark.asyncio
    async def test_process_pdf_should_skip_summary_and_use_text_layer_trade_page(self, monkeypatch):
        summary_page = self.SUMMARY_PAGE
        trade_row_page = self.TRADE_ROW_PAGE

        class FakeLlm:
            def __init__(self):
                self.vision_calls = 0

            async def vision(self, *args, **kwargs):
                self.vision_calls += 1
                return ""

            async def chat(self, *args, **kwargs):
                return '{"trades": [], "broker_detected": "元大證券", "warnings": []}'

        monkeypatch.setattr(ocr_module, "PDF_SUPPORT", True)
        monkeypatch.setattr(
            ocr_module,
            "convert_from_bytes",
            lambda *args, **kwargs: [Image.new("RGB", (10, 10)), Image.new("RGB", (10, 10))],
        )
        monkeypatch.setattr(
            OcrService,
            "_extract_pdf_page_texts",
            lambda _service, content, pdf_password: [summary_page, trade_row_page],
        )

        service = OcrService()
        fake_llm = FakeLlm()
        service.llm = fake_llm
        service.ocr_provider = OcrProvider.GEMINI

        response = await service.process(
            b"%PDF",
            OcrRequest(user_id="1", filename="statement.pdf", content_type="application/pdf"),
        )

        assert fake_llm.vision_calls == 1
        assert len(response.trades) == 1
        assert response.trades[0].ticker == "2308"

    @pytest.mark.asyncio
    async def test_process_pdf_should_send_uncertain_page_to_vision(self, monkeypatch):
        summary_page = self.SUMMARY_PAGE

        class FakeLlm:
            def __init__(self):
                self.vision_calls = 0

            async def vision(self, *args, **kwargs):
                self.vision_calls += 1
                return (
                    "2025/11/03 2025/11/05 集中 TWD 買 "
                    "2308 台達電 10 988.0000 9,880 8 (9,888)"
                )

            async def chat(self, *args, **kwargs):
                return '{"trades": [], "broker_detected": "元大證券", "warnings": []}'

        monkeypatch.setattr(ocr_module, "PDF_SUPPORT", True)
        monkeypatch.setattr(
            ocr_module,
            "convert_from_bytes",
            lambda *args, **kwargs: [Image.new("RGB", (10, 10)), Image.new("RGB", (10, 10))],
        )
        monkeypatch.setattr(
            OcrService,
            "_extract_pdf_page_texts",
            lambda _service, content, pdf_password: [summary_page, ""],
        )

        service = OcrService()
        fake_llm = FakeLlm()
        service.llm = fake_llm
        service.ocr_provider = OcrProvider.GEMINI

        response = await service.process(
            b"%PDF",
            OcrRequest(user_id="1", filename="statement.pdf", content_type="application/pdf"),
        )

        assert fake_llm.vision_calls == 1
        assert len(response.trades) == 1
        assert response.trades[0].ticker == "2308"
