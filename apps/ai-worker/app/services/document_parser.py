"""Document parsing utilities for RAG ingestion."""

from __future__ import annotations

import io
import sys
from pathlib import Path

import anyio
import structlog
from PIL import Image

from app.config import get_settings
from app.services.tesseract_service import TesseractService

logger = structlog.get_logger()

_ocr_semaphore: anyio.Semaphore | None = None


def init_ocr_limits(concurrency: int) -> None:
    """Initialize OCR concurrency limit."""
    global _ocr_semaphore
    _ocr_semaphore = anyio.Semaphore(max(1, concurrency))


def _get_ocr_semaphore() -> anyio.Semaphore | None:
    return _ocr_semaphore

try:
    from pdf2image import convert_from_bytes

    PDF_SUPPORT = True
except ImportError:
    PDF_SUPPORT = False
    convert_from_bytes = None  # type: ignore


class DocumentParser:
    """Extract raw text from supported document types."""

    def __init__(self) -> None:
        self.settings = get_settings()
        self.tesseract = TesseractService()

    async def extract_text(
        self,
        filename: str | None,
        content_type: str | None,
        content: bytes,
    ) -> str:
        ext = Path(filename or "").suffix.lower()
        content_type = (content_type or "").lower()

        if content_type.startswith("text/") or ext in {".txt", ".md", ".csv", ".json"}:
            return content.decode("utf-8-sig", errors="ignore")

        if content_type in {"application/pdf", "application/x-pdf"} or ext == ".pdf":
            return await self._extract_text_from_pdf(content)

        raise ValueError(f"Unsupported file type: {content_type or ext or 'unknown'}")

    async def _extract_text_from_pdf(self, content: bytes) -> str:
        if not PDF_SUPPORT or convert_from_bytes is None:
            raise ValueError("PDF support not available (pdf2image missing)")

        if not self.tesseract.is_available():
            raise ValueError("Tesseract is required to OCR PDF content")

        semaphore = _get_ocr_semaphore()
        if semaphore:
            async with semaphore:
                return await anyio.to_thread.run_sync(self._pdf_to_text_sync, content)
        return await anyio.to_thread.run_sync(self._pdf_to_text_sync, content)

    def _pdf_to_text_sync(self, content: bytes) -> str:
        poppler_path = None
        if sys.platform.startswith("win"):
            candidates = [
                Path("C:/poppler/Library/bin"),
                Path("C:/poppler/poppler-25.12.0/Library/bin"),
            ]
            for candidate in candidates:
                if candidate.exists():
                    poppler_path = str(candidate)
                    break

        max_pages = self.settings.pdf_max_pages
        kwargs = {"dpi": self.settings.pdf_render_dpi}
        if max_pages and max_pages > 0:
            kwargs["first_page"] = 1
            kwargs["last_page"] = max_pages

        try:
            images = (
                convert_from_bytes(
                    content,
                    poppler_path=poppler_path,
                    **kwargs,
                )
                if poppler_path
                else convert_from_bytes(content, **kwargs)
            )
        except Exception as exc:
            logger.error("PDF conversion failed", error=str(exc))
            raise ValueError(f"PDF conversion failed: {exc}") from exc

        if not images:
            raise ValueError("No pages extracted from PDF")

        max_pixels = self.settings.pdf_max_total_pixels
        if max_pixels and max_pixels > 0:
            total_pixels = sum(img.width * img.height for img in images if isinstance(img, Image.Image))
            if total_pixels > max_pixels:
                raise ValueError("PDF exceeds max pixel limit")

        texts: list[str] = []
        for img in images:
            if not isinstance(img, Image.Image):
                continue
            buffer = io.BytesIO()
            img.save(buffer, format="PNG")
            page_text = self.tesseract.extract_text_simple(buffer.getvalue())
            if page_text:
                texts.append(page_text.strip())

        return "\n\n".join(texts).strip()
