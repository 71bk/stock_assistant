"""RAG Ingestion API endpoints."""

from typing import Annotated

import structlog
from fastapi import APIRouter, File, Form, HTTPException, UploadFile

from app.models.schemas import IngestRequest, IngestResponse

router = APIRouter()
logger = structlog.get_logger()


@router.post("", response_model=IngestResponse)
async def ingest_document(
    file: Annotated[UploadFile, File(description="Document file to ingest")],
    user_id: Annotated[str, Form(description="User ID for ownership")],
    title: Annotated[str | None, Form(description="Document title")] = None,
    source_type: Annotated[str, Form(description="Source type")] = "upload",
    tags: Annotated[str | None, Form(description="Comma-separated tags")] = None,
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
    - (Future) Word documents, HTML

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

    # TODO: Implement actual ingestion logic
    # For now, return a placeholder response

    return IngestResponse(
        document_id="doc_placeholder",
        title=title or file.filename or "Untitled",
        chunks_count=0,
        status="pending",
        message="Ingestion service not yet implemented",
    )


@router.post("/text", response_model=IngestResponse)
async def ingest_text(
    text: Annotated[str, Form(description="Text content to ingest")],
    user_id: Annotated[str, Form(description="User ID for ownership")],
    title: Annotated[str, Form(description="Document title")],
    source_type: Annotated[str, Form(description="Source type")] = "note",
    tags: Annotated[str | None, Form(description="Comma-separated tags")] = None,
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

    # TODO: Implement actual ingestion logic

    return IngestResponse(
        document_id="doc_placeholder",
        title=title,
        chunks_count=0,
        status="pending",
        message="Ingestion service not yet implemented",
    )
