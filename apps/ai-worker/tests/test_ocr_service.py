"""Tests for OCR service."""

from datetime import date

import pytest

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

    def test_parse_invalid_date(self):
        """Test parsing invalid date strings."""
        assert OcrService.parse_roc_date("invalid") is None
        assert OcrService.parse_roc_date("") is None
        assert OcrService.parse_roc_date("12345") is None
