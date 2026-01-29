"""Tesseract OCR Service - local OCR using Tesseract."""

import io
from pathlib import Path

import structlog
from PIL import Image

from app.config import get_settings

logger = structlog.get_logger()

# Try to import pytesseract
try:
    import pytesseract
    TESSERACT_AVAILABLE = True
except ImportError:
    TESSERACT_AVAILABLE = False
    pytesseract = None  # type: ignore


class TesseractService:
    """Service for local OCR using Tesseract."""

    def __init__(self) -> None:
        """Initialize Tesseract service."""
        self.settings = get_settings()
        self._available: bool | None = None
        
        # Set custom path if configured
        if self.settings.tesseract_path and pytesseract:
            pytesseract.pytesseract.tesseract_cmd = self.settings.tesseract_path

    def is_available(self) -> bool:
        """Check if Tesseract is installed and working."""
        if not TESSERACT_AVAILABLE:
            logger.warning("pytesseract not installed")
            return False
            
        if self._available is not None:
            return self._available
            
        try:
            # Try to get Tesseract version
            version = pytesseract.get_tesseract_version()
            logger.info("Tesseract available", version=str(version))
            self._available = True
        except Exception as e:
            logger.warning("Tesseract not available", error=str(e))
            self._available = False
            
        return self._available

    def extract_text(
        self,
        image_bytes: bytes,
        lang: str = "chi_tra+eng",
    ) -> tuple[str, float]:
        """
        Extract text from an image using Tesseract.

        Args:
            image_bytes: Image content as bytes
            lang: Language codes (default: Traditional Chinese + English)

        Returns:
            Tuple of (extracted_text, confidence_score)
        """
        if not self.is_available():
            raise RuntimeError("Tesseract is not available")

        try:
            # Load image from bytes
            image = Image.open(io.BytesIO(image_bytes))
            
            # Convert to RGB if needed (Tesseract works better with RGB)
            if image.mode != "RGB":
                image = image.convert("RGB")

            # Extract text with detailed data for confidence
            data = pytesseract.image_to_data(
                image,
                lang=lang,
                output_type=pytesseract.Output.DICT,
            )

            # Build text from words
            words = []
            confidences = []
            
            for i, text in enumerate(data["text"]):
                conf = int(data["conf"][i])
                if text.strip() and conf > 0:
                    words.append(text)
                    confidences.append(conf)

            extracted_text = " ".join(words)
            
            # Calculate average confidence (0-100 scale from Tesseract, convert to 0-1)
            avg_confidence = sum(confidences) / len(confidences) / 100 if confidences else 0.0

            logger.info(
                "Tesseract extraction complete",
                text_length=len(extracted_text),
                word_count=len(words),
                confidence=avg_confidence,
            )

            return extracted_text, avg_confidence

        except Exception as e:
            logger.error("Tesseract extraction failed", error=str(e))
            raise

    def extract_text_simple(
        self,
        image_bytes: bytes,
        lang: str = "chi_tra+eng",
    ) -> str:
        """
        Simple text extraction without confidence score.

        Args:
            image_bytes: Image content as bytes
            lang: Language codes

        Returns:
            Extracted text
        """
        if not self.is_available():
            raise RuntimeError("Tesseract is not available")

        image = Image.open(io.BytesIO(image_bytes))
        
        if image.mode != "RGB":
            image = image.convert("RGB")

        return pytesseract.image_to_string(image, lang=lang)
