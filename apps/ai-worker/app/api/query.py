"""RAG Query API endpoints."""

import structlog
from fastapi import APIRouter, HTTPException

from app.models.schemas import RagQueryRequest, RagQueryResponse, RagChunkResult
from app.services.rag_service import RagService

router = APIRouter()
logger = structlog.get_logger()


@router.post("", response_model=RagQueryResponse)
async def query_rag(request: RagQueryRequest) -> RagQueryResponse:
    """Query the RAG vector store for relevant chunks."""
    service = RagService()

    try:
        chunks = await service.query(
            user_id=request.user_id,
            query_text=request.query,
            top_k=request.top_k,
            source_type=request.source_type,
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except Exception as exc:
        logger.error("RAG query failed", error=str(exc))
        raise HTTPException(status_code=500, detail="RAG query failed") from exc

    # Convert distance to score (1 - distance for cosine distance)
    results = [
        RagChunkResult(
            content=chunk.get("content", ""),
            document_id=chunk.get("document_id", 0),
            chunk_index=chunk.get("chunk_index", 0),
            score=max(0.0, 1.0 - float(chunk.get("distance", 0.0))),
            title=chunk.get("title"),
            source_type=chunk.get("source_type"),
            source_id=chunk.get("source_id"),
        )
        for chunk in chunks
    ]

    return RagQueryResponse(chunks=results)
