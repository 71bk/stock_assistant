"""RAG Ingestion API endpoints."""

from typing import Annotated

import structlog
from fastapi import APIRouter, File, Form, HTTPException, UploadFile

from app.models.schemas import IngestResponse, IngestUrlRequest
from app.services.rag_service import RagService, IngestRateLimitError

router = APIRouter()
logger = structlog.get_logger()


@router.post("", response_model=IngestResponse)
async def ingest_document(
    file: Annotated[UploadFile, File(description="Document file to ingest")],
    user_id: Annotated[str, Form(description="User ID for ownership")],
    title: Annotated[str | None, Form(description="Document title")] = None,
    source_type: Annotated[str, Form(description="Source type")] = "upload",
    tags: Annotated[str | None, Form(description="Comma-separated tags")] = None,
    source_id: Annotated[str | None, Form(description="Source reference ID")] = None,
) -> IngestResponse:
    """
    Ingest a document into the RAG knowledge base.

    **Flow:**
    1. Receive uploaded document
    2. Extract text content
    3. Split into chunks
    4. Generate embeddings
    5. Store in pgvector

    **Supported formats:**
    - PDF
    - Text files (.txt, .md)

    **Response includes:**
    - `document_id`: ID of the created document
    - `chunks_count`: Number of chunks created
    - `status`: Processing status
    """
    logger.info(
        "Ingest request received",
        user_id=user_id,
        filename=file.filename,
        source_type=source_type,
    )

    # Parse user_id to int
    try:
        uid = int(user_id)
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid user_id format")

    # Parse tags
    tag_list = [t.strip() for t in tags.split(",")] if tags else None

    # Read file content
    content = await file.read()
    if not content:
        raise HTTPException(status_code=400, detail="Empty file")

    service = RagService()

    try:
        document_id, chunks_count = await service.ingest_file(
            user_id=uid,
            filename=file.filename,
            content_type=file.content_type,
            content=content,
            title=title,
            source_type=source_type,
            tags=tag_list,
            source_id=source_id,
        )
    except IngestRateLimitError:
        raise HTTPException(
            status_code=429,
            detail="Too many concurrent ingestion requests. Please retry later.",
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    except Exception as exc:
        logger.error("Ingest failed", error=str(exc), exc_info=True)
        raise HTTPException(status_code=500, detail="Ingestion failed")

    return IngestResponse(
        document_id=str(document_id),
        title=title or file.filename or "Untitled",
        chunks_count=chunks_count,
        status="completed",
        message="Document ingested successfully",
    )


@router.post("/text", response_model=IngestResponse)
async def ingest_text(
    text: Annotated[str, Form(description="Text content to ingest")],
    user_id: Annotated[str, Form(description="User ID for ownership")],
    title: Annotated[str, Form(description="Document title")],
    source_type: Annotated[str, Form(description="Source type")] = "note",
    tags: Annotated[str | None, Form(description="Comma-separated tags")] = None,
    source_id: Annotated[str | None, Form(description="Source reference ID")] = None,
) -> IngestResponse:
    """
    Ingest raw text into the RAG knowledge base.

    Useful for notes, memos, or text copied from other sources.
    """
    logger.info(
        "Ingest text request",
        user_id=user_id,
        title=title,
        text_length=len(text),
    )

    # Parse user_id to int
    try:
        uid = int(user_id)
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid user_id format")

    if not text or not text.strip():
        raise HTTPException(status_code=400, detail="Text content is empty")

    # Parse tags
    tag_list = [t.strip() for t in tags.split(",")] if tags else None

    service = RagService()

    try:
        document_id, chunks_count = await service.ingest_text(
            user_id=uid,
            title=title,
            text=text,
            source_type=source_type,
            tags=tag_list,
            source_id=source_id,
        )
    except IngestRateLimitError:
        raise HTTPException(
            status_code=429,
            detail="Too many concurrent ingestion requests. Please retry later.",
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    except Exception as exc:
        logger.error("Ingest text failed", error=str(exc), exc_info=True)
        raise HTTPException(status_code=500, detail="Text ingestion failed")

    return IngestResponse(
        document_id=str(document_id),
        title=title,
        chunks_count=chunks_count,
        status="completed",
        message="Text ingested successfully",
    )


@router.post("/url", response_model=IngestResponse)
async def ingest_url(request: IngestUrlRequest) -> IngestResponse:
    """
    Ingest a document from a presigned URL.
    """
    logger.info(
        "Ingest URL request",
        user_id=request.user_id,
        source_type=request.source_type,
    )

    service = RagService()

    try:
        document_id, chunks_count = await service.ingest_url(
            user_id=request.user_id,
            file_url=request.file_url,
            title=request.title,
            source_type=request.source_type,
            tags=request.tags,
            source_id=request.source_id,
            filename=request.filename,
            content_type=request.content_type,
        )
    except IngestRateLimitError:
        raise HTTPException(
            status_code=429,
            detail="Too many concurrent ingestion requests. Please retry later.",
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    except Exception as exc:
        logger.error("Ingest URL failed", error=str(exc), exc_info=True)
        raise HTTPException(status_code=500, detail="URL ingestion failed")

    return IngestResponse(
        document_id=str(document_id),
        title=request.title or request.filename or "Untitled",
        chunks_count=chunks_count,
        status="completed",
        message="URL ingested successfully",
    )
