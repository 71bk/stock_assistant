"""RAG Query API endpoints."""

import structlog
from fastapi import APIRouter, HTTPException

from app.models.schemas import RagQueryRequest, RagQueryResponse
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

    return RagQueryResponse(chunks=chunks)
