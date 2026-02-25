"""Health check endpoints."""

from __future__ import annotations

import asyncio

import httpx
from fastapi import APIRouter, Response, status
from redis import asyncio as redis

from app.config import LlmProvider, Settings, get_settings
from app.db.rag_repository import get_pool

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
async def readiness_check(response: Response) -> dict:
    """
    Readiness check endpoint.

    Checks if all dependencies are available.
    """
    settings = get_settings()
    database_status, redis_status, llm_status = await asyncio.gather(
        _check_database(),
        _check_redis(settings.redis_url),
        _check_llm(settings),
    )

    checks = {
        "database": database_status,
        "redis": redis_status,
        "llm": llm_status,
    }
    ready = all(value == "ok" for value in checks.values())
    if not ready:
        response.status_code = status.HTTP_503_SERVICE_UNAVAILABLE

    return {
        "status": "ready" if ready else "not_ready",
        "checks": checks,
    }


async def _check_database() -> str:
    try:
        pool = get_pool()
        async with pool.connection() as conn:
            async with conn.cursor() as cur:
                await cur.execute("SELECT 1")
                row = await cur.fetchone()
                if not row:
                    return "error:empty_result"
        return "ok"
    except Exception as exc:  # noqa: BLE001
        return f"error:{exc.__class__.__name__}"


async def _check_redis(redis_url: str) -> str:
    client = redis.from_url(
        redis_url,
        socket_connect_timeout=1.0,
        socket_timeout=1.0,
    )
    try:
        pong = await client.ping()
        return "ok" if pong else "error:ping_failed"
    except Exception as exc:  # noqa: BLE001
        return f"error:{exc.__class__.__name__}"
    finally:
        await client.aclose()


async def _check_llm(settings: Settings) -> str:
    try:
        if settings.llm_provider == LlmProvider.OPENAI:
            return await _check_openai(settings.openai_base_url, settings.openai_api_key)
        if settings.llm_provider == LlmProvider.OLLAMA:
            return await _check_ollama(settings.ollama_base_url)
        return _check_gemini(settings.gemini_api_key)
    except Exception as exc:  # noqa: BLE001
        return f"error:{exc.__class__.__name__}"


async def _check_openai(base_url: str, api_key: str) -> str:
    if not api_key:
        return "error:missing_api_key"
    url = base_url.rstrip("/") + "/models"
    async with httpx.AsyncClient(timeout=2.0) as client:
        response = await client.get(url, headers={"Authorization": f"Bearer {api_key}"})
    return "ok" if response.status_code == 200 else f"error:http_{response.status_code}"


async def _check_ollama(base_url: str) -> str:
    url = base_url.rstrip("/") + "/api/tags"
    async with httpx.AsyncClient(timeout=2.0) as client:
        response = await client.get(url)
    return "ok" if response.status_code == 200 else f"error:http_{response.status_code}"


def _check_gemini(api_key: str) -> str:
    return "ok" if api_key else "error:missing_api_key"
