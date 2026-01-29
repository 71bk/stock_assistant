"""Health check endpoints."""

from fastapi import APIRouter

from app.config import get_settings

router = APIRouter()


@router.get("/health")
async def health_check() -> dict:
    """
    Health check endpoint.

    Returns service status and basic info.
    """
    settings = get_settings()
    return {
        "status": "healthy",
        "service": settings.service_name,
        "environment": settings.environment,
        "version": "0.1.0",
    }


@router.get("/ready")
async def readiness_check() -> dict:
    """
    Readiness check endpoint.

    Checks if all dependencies are available.
    TODO: Add actual checks for OpenAI, DB, Redis
    """
    return {
        "status": "ready",
        "checks": {
            "openai": "ok",
            "database": "ok",
            "redis": "ok",
        },
    }
