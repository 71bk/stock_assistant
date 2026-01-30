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

    # ============================================================
    # OpenAI (if using OpenAI provider)
    # ============================================================
    openai_api_key: str = ""
    openai_base_url: str = "https://api.openai.com/v1"

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
    gemini_embedding_model: str = "text-embedding-004"

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
        if self.llm_provider == LlmProvider.OPENAI:
            return "text-embedding-3-small"
        elif self.llm_provider == LlmProvider.OLLAMA:
            return self.ollama_embedding_model
        else:  # Gemini
            return self.gemini_embedding_model

    # ============================================================
    # Database
    # ============================================================
    database_url: str = "postgresql://postgres:postgres@localhost:5432/invest_assistant"

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

    # ============================================================
    # OCR Settings
    # ============================================================
    tesseract_path: str = ""  # Custom Tesseract path (Windows may need this)
    ocr_min_confidence: float = 0.5  # Minimum confidence to accept Tesseract result
    ocr_min_text_length: int = 20  # Minimum text length to accept
    ocr_fallback_to_vision: bool = True  # Fallback to Vision LLM if Tesseract fails


@lru_cache
def get_settings() -> Settings:
    """Get cached settings instance."""
    return Settings()
