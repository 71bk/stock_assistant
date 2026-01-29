"""Embedding Service - handles text embedding generation."""

import structlog
from openai import AsyncOpenAI

from app.config import get_settings

logger = structlog.get_logger()


class EmbeddingService:
    """Service for generating text embeddings."""

    def __init__(self) -> None:
        """Initialize embedding service."""
        self.settings = get_settings()
        self.client = AsyncOpenAI(api_key=self.settings.openai_api_key)

    async def embed(self, text: str) -> list[float]:
        """
        Generate embedding for a single text.

        Args:
            text: Text to embed

        Returns:
            Embedding vector as list of floats
        """
        response = await self.client.embeddings.create(
            model=self.settings.embedding_model,
            input=text,
            dimensions=self.settings.embedding_dimension,
        )

        return response.data[0].embedding

    async def embed_batch(self, texts: list[str]) -> list[list[float]]:
        """
        Generate embeddings for multiple texts.

        Args:
            texts: List of texts to embed

        Returns:
            List of embedding vectors
        """
        if not texts:
            return []

        logger.debug("Generating embeddings", count=len(texts))

        response = await self.client.embeddings.create(
            model=self.settings.embedding_model,
            input=texts,
            dimensions=self.settings.embedding_dimension,
        )

        # Sort by index to maintain order
        sorted_data = sorted(response.data, key=lambda x: x.index)
        embeddings = [item.embedding for item in sorted_data]

        logger.debug(
            "Embeddings generated",
            count=len(embeddings),
            usage=response.usage.model_dump() if response.usage else None,
        )

        return embeddings


def chunk_text(
    text: str,
    chunk_size: int = 500,
    chunk_overlap: int = 50,
) -> list[dict]:
    """
    Split text into overlapping chunks.

    Args:
        text: Text to split
        chunk_size: Maximum characters per chunk
        chunk_overlap: Number of characters to overlap

    Returns:
        List of chunk dicts with content and metadata
    """
    if not text:
        return []

    chunks = []
    start = 0
    chunk_index = 0

    while start < len(text):
        end = start + chunk_size

        # Try to break at sentence boundary
        if end < len(text):
            # Look for sentence end (.!?) within last 100 chars
            search_start = max(end - 100, start)
            best_break = end

            for i in range(end, search_start, -1):
                if text[i - 1] in ".!?。！？\n":
                    best_break = i
                    break

            end = best_break

        chunk_content = text[start:end].strip()

        if chunk_content:
            chunks.append(
                {
                    "chunk_index": chunk_index,
                    "content": chunk_content,
                    "start_char": start,
                    "end_char": end,
                }
            )
            chunk_index += 1

        # Move start forward, accounting for overlap
        start = end - chunk_overlap
        if start >= len(text) - chunk_overlap:
            break

    return chunks
