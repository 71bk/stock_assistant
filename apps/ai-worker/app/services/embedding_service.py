"""Embedding Service - handles text embedding generation."""

import structlog
from openai import AsyncOpenAI

from app.config import EmbeddingProvider, get_settings

logger = structlog.get_logger()


class EmbeddingService:
    """Service for generating text embeddings."""

    def __init__(self) -> None:
        """Initialize embedding service."""
        self.settings = get_settings()
        self.provider = self.settings.resolved_embedding_provider
        self.genai = None

        if self.provider == EmbeddingProvider.OPENAI:
            self.client = AsyncOpenAI(
                api_key=self.settings.openai_api_key,
                base_url=self.settings.openai_base_url,
            )
        elif self.provider == EmbeddingProvider.OLLAMA:
            # Ollama uses OpenAI-compatible API
            self.client = AsyncOpenAI(
                api_key="ollama",  # Ollama doesn't need real API key
                base_url=f"{self.settings.ollama_base_url}/v1",
            )
        else:  # Gemini
            import google.generativeai as genai

            genai.configure(api_key=self.settings.gemini_api_key)
            self.genai = genai
            self.client = None  # Gemini uses different API

    async def embed(self, text: str) -> list[float]:
        """
        Generate embedding for a single text.

        Args:
            text: Text to embed

        Returns:
            Embedding vector as list of floats
        """
        if self.provider == EmbeddingProvider.GEMINI:
            return await self._embed_gemini(text)

        response = await self.client.embeddings.create(
            model=self.settings.embedding_model_name,
            input=text,
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

        logger.debug("Generating embeddings", count=len(texts), provider=self.provider.value)

        if self.provider == EmbeddingProvider.GEMINI:
            return await self._embed_batch_gemini(texts)

        response = await self.client.embeddings.create(
            model=self.settings.embedding_model_name,
            input=texts,
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

    async def _embed_gemini(self, text: str) -> list[float]:
        """Generate embedding using Gemini API."""
        import asyncio

        def _sync_embed():
            result = self.genai.embed_content(
                model=f"models/{self.settings.gemini_embedding_model}",
                content=text,
                task_type="retrieval_document",
                output_dimensionality=self.settings.embedding_dimension,
            )
            return result["embedding"]

        return await asyncio.to_thread(_sync_embed)

    async def _embed_batch_gemini(self, texts: list[str]) -> list[list[float]]:
        """Generate batch embeddings using Gemini API."""
        import asyncio

        def _sync_embed_batch():
            result = self.genai.embed_content(
                model=f"models/{self.settings.gemini_embedding_model}",
                content=texts,
                task_type="retrieval_document",
                output_dimensionality=self.settings.embedding_dimension,
            )

            embeddings = None
            if isinstance(result, dict):
                embeddings = result.get("embedding") or result.get("embeddings")
            else:
                embeddings = getattr(result, "embedding", None) or getattr(result, "embeddings", None)

            if embeddings is None:
                raise ValueError("Gemini embedding response missing embeddings")

            # Handle single embedding returned as list[float]
            if embeddings and isinstance(embeddings[0], (int, float)):
                return [embeddings]

            # Handle list of dicts with values
            if embeddings and isinstance(embeddings[0], dict) and "values" in embeddings[0]:
                return [item["values"] for item in embeddings]

            return embeddings

        return await asyncio.to_thread(_sync_embed_batch)
