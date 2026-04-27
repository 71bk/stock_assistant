"""Guards for RAG schema and embedding dimension alignment."""

from __future__ import annotations

import re


_VECTOR_TYPE_PATTERN = re.compile(r"^vector\((\d+)\)$")


def parse_vector_dimension(column_type: str | None) -> int | None:
    """Parse a pgvector column type string like ``vector(1536)``."""
    if column_type is None:
        return None

    normalized = column_type.strip().lower()
    match = _VECTOR_TYPE_PATTERN.fullmatch(normalized)
    if match is None:
        return None
    return int(match.group(1))


def validate_configured_embedding_dimension(expected_dimension: int | None, configured_dimension: int) -> None:
    """Fail fast when provider/model and configured embedding dimensions diverge."""
    if expected_dimension is None:
        raise RuntimeError(
            "Unknown embedding dimension for provider/model. "
            "Set EMBEDDING_EXPECTED_DIMENSION to avoid mismatched vector size."
        )
    if expected_dimension != configured_dimension:
        raise RuntimeError(
            f"Embedding dimension mismatch: expected {expected_dimension} but got "
            f"{configured_dimension}."
        )


def validate_db_embedding_dimension(configured_dimension: int, db_dimension: int | None) -> bool:
    """Check DB vector dimension alignment.

    Returns True if the schema is present and dimensions match.
    Returns False (with warning) if the schema is not yet present.
    Raises RuntimeError if dimensions are present but mismatched.
    """
    if db_dimension is None:
        return False
    if db_dimension != configured_dimension:
        raise RuntimeError(
            "RAG schema dimension mismatch: "
            f"DB vector.rag_chunks.embedding is vector({db_dimension}) but worker is configured for "
            f"{configured_dimension}."
        )
    return True
