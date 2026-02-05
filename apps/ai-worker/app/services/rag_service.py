"""RAG service for ingestion and query."""

from __future__ import annotations

from typing import Any

import anyio
import httpx
import random
import structlog

try:
    from openai import APIError as OpenAIAPIError
    from openai import APIConnectionError as OpenAIConnectionError
    from openai import RateLimitError as OpenAIRateLimitError
except ImportError:
    OpenAIAPIError = None
    OpenAIConnectionError = None
    OpenAIRateLimitError = None

from app.config import get_settings
from app.db.rag_repository import RagRepository
from app.services.document_parser import DocumentParser
from app.services.embedding_service import EmbeddingService
from app.services.text_splitter import chunk_text

logger = structlog.get_logger()

_ingest_semaphore: anyio.Semaphore | None = None


class IngestRateLimitError(RuntimeError):
    """Raised when ingestion concurrency limit is exceeded."""


def init_ingest_limits(concurrency: int) -> None:
    """Initialize ingestion concurrency limit."""
    global _ingest_semaphore
    _ingest_semaphore = anyio.Semaphore(max(1, concurrency))


def _get_ingest_semaphore() -> anyio.Semaphore | None:
    return _ingest_semaphore


class RagService:
    """Service to ingest documents and query chunks."""

    def __init__(self) -> None:
        self.settings = get_settings()
        self.repo = RagRepository()
        self.embedder = EmbeddingService()
        self.parser = DocumentParser()

    async def ingest_text(
        self,
        user_id: int,
        title: str | None,
        text: str,
        source_type: str,
        tags: list[str] | None = None,
        source_id: str | None = None,
    ) -> tuple[int, int]:
        semaphore = _get_ingest_semaphore()
        if semaphore:
            if self.settings.ingest_reject_on_limit:
                try:
                    async with anyio.fail_after(0):
                        await semaphore.acquire()
                except TimeoutError as exc:
                    raise IngestRateLimitError("Ingestion concurrency limit reached") from exc
                try:
                    return await self._ingest_text_internal(
                        user_id,
                        title,
                        text,
                        source_type,
                        tags,
                        source_id,
                    )
                finally:
                    semaphore.release()
            async with semaphore:
                return await self._ingest_text_internal(
                    user_id,
                    title,
                    text,
                    source_type,
                    tags,
                    source_id,
                )
        return await self._ingest_text_internal(
            user_id,
            title,
            text,
            source_type,
            tags,
            source_id,
        )

    async def _ingest_text_internal(
        self,
        user_id: int,
        title: str | None,
        text: str,
        source_type: str,
        tags: list[str] | None,
        source_id: str | None,
    ) -> tuple[int, int]:
        if not text or not text.strip():
            raise ValueError("Text content is empty")

        chunks = chunk_text(
            text=text,
            chunk_size=self.settings.chunk_size,
            chunk_overlap=self.settings.chunk_overlap,
        )

        if not chunks:
            raise ValueError("No chunks generated from text")

        embeddings = await self._embed_with_fallbacks([c["content"] for c in chunks])
        meta: dict[str, Any] = {"tags": tags or []}

        document_id, chunk_count = await self.repo.insert_document_with_chunks(
            user_id=user_id,
            title=title,
            source_type=source_type,
            source_id=source_id,
            meta=meta,
            chunks=chunks,
            embeddings=embeddings,
        )

        logger.info(
            "RAG ingestion completed",
            user_id=user_id,
            document_id=document_id,
            chunk_count=chunk_count,
        )

        return document_id, chunk_count

    async def ingest_file(
        self,
        user_id: int,
        filename: str | None,
        content_type: str | None,
        content: bytes,
        title: str | None,
        source_type: str,
        tags: list[str] | None = None,
        source_id: str | None = None,
    ) -> tuple[int, int]:
        """Ingest file with semaphore protecting both parsing and ingestion."""
        semaphore = _get_ingest_semaphore()
        if semaphore:
            async with semaphore:
                text = await self.parser.extract_text(filename, content_type, content)
                resolved_title = title or filename or "Untitled"
                return await self._ingest_text_internal(
                    user_id=user_id,
                    title=resolved_title,
                    text=text,
                    source_type=source_type,
                    tags=tags,
                    source_id=source_id,
                )
        text = await self.parser.extract_text(filename, content_type, content)
        resolved_title = title or filename or "Untitled"
        return await self._ingest_text_internal(
            user_id=user_id,
            title=resolved_title,
            text=text,
            source_type=source_type,
            tags=tags,
            source_id=source_id,
        )

    async def query(
        self,
        user_id: int,
        query_text: str,
        top_k: int = 5,
        source_type: str | None = None,
    ) -> list[dict[str, Any]]:
        if not query_text or not query_text.strip():
            raise ValueError("Query text is empty")

        query_vector = await self._embed_single_with_retry(query_text)
        return await self.repo.search_chunks(
            user_id=user_id,
            query_vector=query_vector,
            top_k=top_k,
            source_type=source_type,
        )

    async def _embed_with_fallbacks(self, texts: list[str]) -> list[list[float]]:
        batch_sizes = [self.settings.embedding_batch_size] + self.settings.embedding_batch_fallbacks_list
        last_exc: Exception | None = None

        for batch_size in batch_sizes:
            try:
                return await self._embed_in_batches(texts, batch_size)
            except Exception as exc:
                last_exc = exc
                if not self._is_retryable(exc):
                    raise
                logger.warning(
                    "Embedding failed, trying smaller batch size",
                    batch_size=batch_size,
                    error=str(exc),
                )

        if last_exc:
            raise last_exc
        raise RuntimeError("Embedding failed with all batch sizes")

    async def _embed_in_batches(self, texts: list[str], batch_size: int) -> list[list[float]]:
        embeddings: list[list[float]] = []
        for i in range(0, len(texts), batch_size):
            batch = texts[i : i + batch_size]
            embeddings.extend(await self._embed_batch_with_retry(batch, batch_size))
        return embeddings

    async def _embed_batch_with_retry(
        self,
        batch: list[str],
        batch_size: int,
    ) -> list[list[float]]:
        max_attempts = self.settings.embedding_max_retries
        for attempt in range(max_attempts):
            try:
                return await self.embedder.embed_batch(batch)
            except Exception as exc:
                is_last_attempt = attempt >= max_attempts - 1
                if not self._is_retryable(exc) or is_last_attempt:
                    raise
                delay = min(
                    self.settings.embedding_backoff_cap,
                    self.settings.embedding_backoff_base * (2**attempt),
                )
                delay += random.uniform(0.0, 0.3)
                logger.warning(
                    "Embedding batch failed, retrying",
                    batch_size=batch_size,
                    attempt=attempt + 1,
                    max_attempts=max_attempts,
                    delay=delay,
                    error=str(exc),
                )
                await anyio.sleep(delay)
        # Should not reach here, but just in case
        raise RuntimeError("Embedding failed after all retries")

    async def _embed_single_with_retry(self, text: str) -> list[float]:
        max_attempts = self.settings.embedding_max_retries
        for attempt in range(max_attempts):
            try:
                return await self.embedder.embed(text)
            except Exception as exc:
                is_last_attempt = attempt >= max_attempts - 1
                if not self._is_retryable(exc) or is_last_attempt:
                    raise
                delay = min(
                    self.settings.embedding_backoff_cap,
                    self.settings.embedding_backoff_base * (2**attempt),
                )
                delay += random.uniform(0.0, 0.3)
                logger.warning(
                    "Embedding query failed, retrying",
                    attempt=attempt + 1,
                    max_attempts=max_attempts,
                    delay=delay,
                    error=str(exc),
                )
                await anyio.sleep(delay)
        raise RuntimeError("Embedding failed after all retries")

    def _is_retryable(self, exc: Exception) -> bool:
        # Handle httpx errors
        if isinstance(exc, httpx.RequestError):
            return True
        if isinstance(exc, httpx.HTTPStatusError):
            status = exc.response.status_code
            if status in {429, 500, 502, 503, 504}:
                return True
            if status in {400, 413}:
                body = (exc.response.text or "").lower()
                if "payload" in body or "too large" in body or "exceed" in body:
                    return True
            return False
        # Handle OpenAI SDK errors
        if OpenAIConnectionError and isinstance(exc, OpenAIConnectionError):
            return True
        if OpenAIRateLimitError and isinstance(exc, OpenAIRateLimitError):
            return True
        if OpenAIAPIError and isinstance(exc, OpenAIAPIError):
            # Retry on server errors (5xx)
            if hasattr(exc, 'status_code') and exc.status_code and exc.status_code >= 500:
                return True
        return False
