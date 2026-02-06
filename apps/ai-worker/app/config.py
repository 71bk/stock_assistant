"""Application configuration using Pydantic Settings."""

from enum import Enum
from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

# Get the directory where this config.py file is located
_CONFIG_DIR = Path(__file__).parent
# .env is in the parent directory (ai-worker root)
_ENV_FILE = _CONFIG_DIR.parent / ".env"


class LlmProvider(str, Enum):
    """Supported LLM providers."""

    OPENAI = "openai"
    OLLAMA = "ollama"
    GEMINI = "gemini"


class EmbeddingProvider(str, Enum):
    """Supported embedding providers."""

    OPENAI = "openai"
    OLLAMA = "ollama"
    GEMINI = "gemini"


class OcrProvider(str, Enum):
    """OCR pipeline providers."""

    AUTO = "auto"
    TESSERACT = "tesseract"
    GEMINI = "gemini"
    OLLAMA = "ollama"
    OPENAI = "openai"


class Settings(BaseSettings):
    """Application settings loaded from environment variables."""

    model_config = SettingsConfigDict(
        env_file=str(_ENV_FILE),
        env_file_encoding="utf-8",
        case_sensitive=False,
    )

    # ============================================================
    # Service Info
    # ============================================================
    service_name: str = "ai-worker"
    environment: str = "dev"
    log_level: str = "INFO"

    # ============================================================
    # LLM Provider Selection
    # ============================================================
    llm_provider: LlmProvider = LlmProvider.OLLAMA  # Default to free Ollama
    embedding_provider: EmbeddingProvider | None = None
    ocr_provider: OcrProvider = OcrProvider.AUTO

    # ============================================================
    # OpenAI (if using OpenAI provider)
    # ============================================================
    openai_api_key: str = ""
    openai_base_url: str = "https://api.openai.com/v1"
    openai_embedding_model: str = "text-embedding-3-small"

    # ============================================================
    # Ollama (if using Ollama provider - FREE & LOCAL)
    # ============================================================
    ollama_base_url: str = "http://localhost:11434"
    ollama_vision_model: str = "llama3.2-vision"  # For image understanding
    ollama_text_model: str = "llama3.2"  # For text processing
    ollama_embedding_model: str = "nomic-embed-text"

    # ============================================================
    # Google Gemini (if using Gemini provider - FREE tier available)
    # ============================================================
    gemini_api_key: str = ""
    gemini_vision_model: str = "gemini-3-pro-preview"  # Supports images
    gemini_text_model: str = "gemini-3-pro-preview"
    gemini_embedding_model: str = "gemini-embedding-001"

    # ============================================================
    # Model Selection (provider-agnostic)
    # ============================================================
    @property
    def vision_model(self) -> str:
        """Get the vision model based on provider."""
        if self.llm_provider == LlmProvider.OPENAI:
            return "gpt-4o-mini"
        elif self.llm_provider == LlmProvider.OLLAMA:
            return self.ollama_vision_model
        else:  # Gemini
            return self.gemini_vision_model

    @property
    def text_model(self) -> str:
        """Get the text model based on provider."""
        if self.llm_provider == LlmProvider.OPENAI:
            return "gpt-4o-mini"
        elif self.llm_provider == LlmProvider.OLLAMA:
            return self.ollama_text_model
        else:  # Gemini
            return self.gemini_text_model

    @property
    def embedding_model_name(self) -> str:
        """Get the embedding model based on provider."""
        provider = self.resolved_embedding_provider
        if provider == EmbeddingProvider.OPENAI:
            return self.openai_embedding_model
        if provider == EmbeddingProvider.OLLAMA:
            return self.ollama_embedding_model
        return self.gemini_embedding_model

    @property
    def resolved_embedding_provider(self) -> EmbeddingProvider:
        """Resolve embedding provider (fallback to LLM provider)."""
        if self.embedding_provider:
            return self.embedding_provider
        return EmbeddingProvider(self.llm_provider.value)

    # ============================================================
    # Database
    # ============================================================
    database_url: str = "postgresql://postgres:postgres@localhost:5432/invest_assistant"
    db_pool_min: int = 2
    db_pool_max: int = 20
    db_pool_timeout: int = 5
    db_pool_max_idle: int = 60

    # ============================================================
    # Redis
    # ============================================================
    redis_url: str = "redis://localhost:6379/0"

    # ============================================================
    # CORS
    # ============================================================
    cors_origins: str = "http://localhost:3000,http://localhost:5173"

    @property
    def cors_origins_list(self) -> list[str]:
        """Parse CORS origins string to list."""
        return [origin.strip() for origin in self.cors_origins.split(",")]

    # ============================================================
    # Chunking
    # ============================================================
    chunk_size: int = 500
    chunk_overlap: int = 50

    # ============================================================
    # Embedding
    # ============================================================
    embedding_dimension: int = 1536
    embedding_expected_dimension: int | None = None
    embedding_batch_size: int = 50
    embedding_batch_fallbacks: str = "25,10"
    embedding_max_inflight: int = 2
    embedding_max_retries: int = 5
    embedding_backoff_base: float = 0.5
    embedding_backoff_cap: float = 8.0

    def resolve_expected_embedding_dimension(self) -> int | None:
        """Resolve expected embedding dimension for fail-fast checks."""
        if self.embedding_expected_dimension is not None:
            return self.embedding_expected_dimension

        provider = self.resolved_embedding_provider.value
        model = self.embedding_model_name

        dimension_table: dict[tuple[str, str], int] = {
            ("openai", "text-embedding-3-small"): 1536,
            ("openai", "text-embedding-3-large"): 3072,
            ("ollama", "nomic-embed-text"): 768,
        }

        return dimension_table.get((provider, model))
    embedding_version: str = "v1.0"

    @property
    def embedding_batch_fallbacks_list(self) -> list[int]:
        """Parse embedding_batch_fallbacks string to list of ints."""
        return [int(x.strip()) for x in self.embedding_batch_fallbacks.split(",") if x.strip()]

    # ============================================================
    # OCR Settings
    # ============================================================
    tesseract_path: str = ""  # Custom Tesseract path (Windows may need this)
    ocr_min_confidence: float = 0.5  # Minimum confidence to accept Tesseract result
    ocr_min_text_length: int = 20  # Minimum text length to accept
    ocr_fallback_to_vision: bool = True  # Fallback to Vision LLM if Tesseract fails
    ocr_concurrency: int = 2

    # ============================================================
    # Ingest Settings
    # ============================================================
    ingest_concurrency: int = 5
    ingest_reject_on_limit: bool = False

    # ============================================================
    # PDF Settings
    # ============================================================
    pdf_render_dpi: int = 200


@lru_cache
def get_settings() -> Settings:
    """Get cached settings instance."""
    return Settings()
