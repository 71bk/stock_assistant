"""RAG repository for pgvector storage."""

from __future__ import annotations

from typing import Any

import structlog
from pgvector.psycopg import register_vector_async
from psycopg.rows import dict_row
from psycopg.types.json import Json
from psycopg_pool import AsyncConnectionPool

from app.config import get_settings
from app.rag_schema_guard import parse_vector_dimension

logger = structlog.get_logger()

_pool: AsyncConnectionPool | None = None


async def init_pool() -> None:
    """Initialize global async connection pool."""
    global _pool
    if _pool is not None:
        return

    settings = get_settings()

    async def _configure(conn) -> None:
        await register_vector_async(conn)

    _pool = AsyncConnectionPool(
        conninfo=settings.database_url,
        min_size=settings.db_pool_min,
        max_size=settings.db_pool_max,
        timeout=settings.db_pool_timeout,
        max_idle=settings.db_pool_max_idle,
        configure=_configure,
    )
    await _pool.open()
    logger.info(
        "DB pool initialized",
        min_size=settings.db_pool_min,
        max_size=settings.db_pool_max,
        timeout=settings.db_pool_timeout,
    )


async def close_pool() -> None:
    """Close global async connection pool."""
    global _pool
    if _pool is None:
        return
    await _pool.close()
    _pool = None
    logger.info("DB pool closed")


def get_pool() -> AsyncConnectionPool:
    if _pool is None:
        raise RuntimeError("DB pool is not initialized")
    return _pool


class RagRepository:
    """Repository for RAG documents and chunks."""

    def __init__(self) -> None:
        self.settings = get_settings()

    async def insert_document_with_chunks(
        self,
        user_id: int,
        title: str | None,
        source_type: str,
        source_id: str | None,
        meta: dict[str, Any],
        chunks: list[dict[str, Any]],
        embeddings: list[list[float]],
        embedding_model: str | None = None,
        embedding_version: str | None = None,
        dimensions: int | None = None,
    ) -> tuple[int, int]:
        if len(chunks) != len(embeddings):
            raise ValueError("Chunks and embeddings length mismatch")

        pool = get_pool()
        async with pool.connection() as conn:
            async with conn.transaction():
                async with conn.cursor() as cur:
                    await cur.execute(
                        """
                        INSERT INTO vector.rag_documents (user_id, source_type, source_id, title, meta)
                        VALUES (%s, %s, %s, %s, %s)
                        RETURNING id
                        """,
                        (user_id, source_type, source_id, title, Json(meta)),
                    )
                    row = await cur.fetchone()
                    if not row:
                        raise ValueError("Failed to insert rag_document")
                    document_id = int(row[0])

                    if chunks:
                        params = []
                        for chunk, embedding in zip(chunks, embeddings, strict=True):
                            chunk_meta = {
                                "start_char": chunk.get("start_char"),
                                "end_char": chunk.get("end_char"),
                            }
                            params.append(
                                (
                                    document_id,
                                    user_id,
                                    int(chunk.get("chunk_index", 0)),
                                    chunk.get("content", ""),
                                    embedding,
                                    Json(chunk_meta),
                                    embedding_model,
                                    embedding_version,
                                    dimensions,
                                )
                            )

                        await cur.executemany(
                            """
                            INSERT INTO vector.rag_chunks
                                (document_id, user_id, chunk_index, content, embedding, meta,
                                 embedding_model, embedding_version, dimensions)
                            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
                            """,
                            params,
                        )

        return document_id, len(chunks)

    async def search_chunks(
        self,
        user_id: int,
        query_vector: list[float],
        top_k: int = 5,
        source_type: str | None = None,
    ) -> list[dict[str, Any]]:
        from pgvector.psycopg import Vector

        where_clauses = ["c.user_id = %s"]
        where_params: list[Any] = [user_id]

        if source_type:
            where_clauses.append("d.source_type = %s")
            where_params.append(source_type)

        # Convert list to Vector for proper type casting
        query_vec = Vector(query_vector)

        sql = f"""
            SELECT
                c.document_id,
                c.chunk_index,
                c.content,
                c.meta,
                d.title,
                d.source_type,
                d.source_id,
                (c.embedding <=> %s) AS distance
            FROM vector.rag_chunks c
            JOIN vector.rag_documents d ON d.id = c.document_id
            WHERE {" AND ".join(where_clauses)}
            ORDER BY c.embedding <=> %s
            LIMIT %s
        """

        params = [query_vec] + where_params + [query_vec, top_k]

        pool = get_pool()
        async with pool.connection() as conn:
            async with conn.cursor(row_factory=dict_row) as cur:
                await cur.execute(sql, params)
                rows = await cur.fetchall()

        return [dict(row) for row in rows]

    async def get_embedding_column_dimension(self) -> int | None:
        pool = get_pool()
        async with pool.connection() as conn:
            async with conn.cursor() as cur:
                await cur.execute(
                    """
                    SELECT format_type(a.atttypid, a.atttypmod) AS column_type
                    FROM pg_attribute a
                    JOIN pg_class c ON c.oid = a.attrelid
                    JOIN pg_namespace n ON n.oid = c.relnamespace
                    WHERE n.nspname = 'vector'
                      AND c.relname = 'rag_chunks'
                      AND a.attname = 'embedding'
                      AND a.attnum > 0
                      AND NOT a.attisdropped
                    """
                )
                row = await cur.fetchone()

        if not row:
            return None

        return parse_vector_dimension(row[0])

    async def delete_document(self, user_id: int, document_id: int) -> bool:
        pool = get_pool()
        async with pool.connection() as conn:
            async with conn.transaction():
                async with conn.cursor() as cur:
                    await cur.execute(
                        """
                        DELETE FROM vector.rag_documents
                        WHERE id = %s AND user_id = %s
                        RETURNING id
                        """,
                        (document_id, user_id),
                    )
                    row = await cur.fetchone()
                    return row is not None
