"""Contract tests for RAG query response payload shape."""

from app.models.schemas import RagChunkResult, RagQueryResponse


def test_rag_chunk_result_uses_score_not_distance() -> None:
    """RAG chunk response must expose score and not legacy fields."""
    chunk = RagChunkResult(
        content="AI Worker",
        document_id=123,
        chunk_index=0,
        score=0.92,
        title="Monthly Report",
        source_type="upload",
        source_id="file-123",
    )

    payload = chunk.model_dump()
    assert "score" in payload
    assert "distance" not in payload
    assert "meta" not in payload


def test_rag_query_response_chunks_follow_chunk_contract() -> None:
    """RAG query response should keep chunk shape consistent."""
    response = RagQueryResponse(
        chunks=[
            RagChunkResult(
                content="AI Worker",
                document_id=123,
                chunk_index=0,
                score=0.92,
            )
        ]
    )

    payload = response.model_dump()
    chunk = payload["chunks"][0]
    assert chunk["score"] == 0.92
    assert "distance" not in chunk
    assert "meta" not in chunk
