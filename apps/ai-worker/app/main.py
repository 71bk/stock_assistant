"""FastAPI Application Entry Point."""

import logging
import sys
from pathlib import Path

# Fix import path when running directly
if __name__ == "__main__":
    sys.path.insert(0, str(Path(__file__).parent.parent))

from contextlib import asynccontextmanager
from typing import AsyncGenerator

import structlog
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api import health, ingest, ocr, query
from app.config import get_settings
from app.db.rag_repository import RagRepository, close_pool, init_pool
from app.rag_schema_guard import (
    validate_configured_embedding_dimension,
    validate_db_embedding_dimension,
)
from app.services.document_parser import init_ocr_limits
from app.services.rag_service import init_ingest_limits

# Configure structured logging
structlog.configure(
    processors=[
        structlog.stdlib.filter_by_level,
        structlog.stdlib.add_logger_name,
        structlog.stdlib.add_log_level,
        structlog.stdlib.PositionalArgumentsFormatter(),
        structlog.processors.TimeStamper(fmt="iso"),
        structlog.processors.StackInfoRenderer(),
        structlog.processors.format_exc_info,
        structlog.processors.UnicodeDecoder(),
        structlog.processors.JSONRenderer(),
    ],
    wrapper_class=structlog.stdlib.BoundLogger,
    context_class=dict,
    logger_factory=structlog.stdlib.LoggerFactory(),
    cache_logger_on_first_use=True,
)

logger = structlog.get_logger()


def configure_stdlib_logging(level_name: str) -> None:
    """Configure standard logging so structlog INFO logs are visible."""
    level = getattr(logging, (level_name or "INFO").upper(), logging.INFO)
    logging.basicConfig(level=level, format="%(message)s", force=True)
    logging.getLogger("uvicorn.error").setLevel(level)
    logging.getLogger("uvicorn.access").setLevel(level)


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncGenerator[None, None]:
    """Application lifespan events."""
    settings = get_settings()
    configure_stdlib_logging(settings.log_level)
    logger.info(
        "Starting AI Worker",
        service=settings.service_name,
        environment=settings.environment,
    )
    expected_dim = settings.resolve_expected_embedding_dimension()
    validate_configured_embedding_dimension(expected_dim, settings.embedding_dimension)
    init_ocr_limits(settings.ocr_concurrency)
    await init_pool()
    db_dimension = await RagRepository().get_embedding_column_dimension()
    schema_ok = validate_db_embedding_dimension(settings.embedding_dimension, db_dimension)
    if not schema_ok:
        logger.warning(
            "RAG schema not ready yet; waiting for backend migrations",
            configured_dimension=settings.embedding_dimension,
        )
    init_ingest_limits(settings.ingest_concurrency)
    yield
    await close_pool()
    logger.info("Shutting down AI Worker")


def create_app() -> FastAPI:
    """Create and configure FastAPI application."""
    settings = get_settings()

    app = FastAPI(
        title="AI Worker",
        description="AI Worker for OCR, RAG ingestion, and analysis - Investment Assistant Platform",
        version="0.1.0",
        docs_url="/docs",
        redoc_url="/redoc",
        lifespan=lifespan,
    )

    # CORS middleware
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins_list,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    # Include routers
    app.include_router(health.router, tags=["Health"])
    app.include_router(ocr.router, prefix="/ocr", tags=["OCR"])
    app.include_router(ingest.router, prefix="/ingest", tags=["RAG Ingestion"])
    app.include_router(query.router, prefix="/query", tags=["RAG Query"])

    return app


# Create app instance
app = create_app()


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        "app.main:app",
        host="0.0.0.0",
        port=8001,
        reload=True,
        reload_dirs=[str(Path(__file__).parent.parent)],
    )
