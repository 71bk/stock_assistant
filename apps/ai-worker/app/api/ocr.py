"""OCR API endpoints."""

from typing import Annotated

import structlog
from fastapi import APIRouter, File, Form, HTTPException, UploadFile

from app.models.schemas import OcrRequest, OcrResponse, ParsedTrade
from app.services.ocr_service import OcrService

router = APIRouter()
logger = structlog.get_logger()


@router.post("", response_model=OcrResponse)
async def process_ocr(
    file: Annotated[UploadFile, File(description="Image or PDF file to process")],
    user_id: Annotated[str, Form(description="User ID for tracking")],
    broker: Annotated[str | None, Form(description="Broker name hint")] = None,
) -> OcrResponse:
    """
    Process an image or PDF file with OCR to extract trade data.

    **Flow:**
    1. Receive uploaded file (image/PDF)
    2. Extract text using Vision LLM (Path A) or OCR + Text LLM (Path B)
    3. Parse extracted text into structured trade data
    4. Return parsed trades with confidence scores

    **Supported formats:**
    - Images: JPEG, PNG, WEBP
    - Documents: PDF (first page or all pages)

    **Response includes:**
    - `raw_text`: Original extracted text
    - `trades`: List of parsed trade objects
    - `confidence`: Overall confidence score (0.0-1.0)
    - `warnings`: Any parsing warnings or issues
    """
    logger.info("OCR request received", user_id=user_id, filename=file.filename, broker=broker)

    # Validate file type
    if file.content_type not in [
        "image/jpeg",
        "image/png",
        "image/webp",
        "application/pdf",
    ]:
        raise HTTPException(
            status_code=400,
            detail=f"Unsupported file type: {file.content_type}. "
            "Supported: JPEG, PNG, WEBP, PDF",
        )

    try:
        # Read file content
        content = await file.read()

        # Create OCR service and process
        ocr_service = OcrService()
        request = OcrRequest(
            user_id=user_id,
            filename=file.filename or "unknown",
            content_type=file.content_type or "application/octet-stream",
            broker=broker,
        )

        result = await ocr_service.process(content, request)

        logger.info(
            "OCR completed",
            user_id=user_id,
            trades_count=len(result.trades),
            confidence=result.confidence,
        )

        return result

    except Exception as e:
        import traceback
        error_msg = str(e) if str(e) else repr(e)
        logger.error("OCR processing failed", user_id=user_id, error=error_msg, traceback=traceback.format_exc())
        raise HTTPException(status_code=500, detail=f"OCR processing failed: {error_msg}") from e


@router.post("/parse-text", response_model=OcrResponse)
async def parse_text(
    text: Annotated[str, Form(description="Raw text to parse")],
    user_id: Annotated[str, Form(description="User ID for tracking")],
    broker: Annotated[str | None, Form(description="Broker name hint")] = None,
) -> OcrResponse:
    """
    Parse raw text into structured trade data.

    Useful when you already have text (e.g., from copy-paste or another OCR source)
    and just need the structured parsing.
    """
    logger.info("Parse text request", user_id=user_id, text_length=len(text), broker=broker)

    try:
        ocr_service = OcrService()
        result = await ocr_service.parse_text(text, user_id, broker)
        return result
    except Exception as e:
        logger.error("Text parsing failed", user_id=user_id, error=str(e))
        raise HTTPException(status_code=500, detail=f"Text parsing failed: {str(e)}") from e
