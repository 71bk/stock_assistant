"""Tests for health check endpoints."""

import pytest
from httpx import ASGITransport, AsyncClient

from app.api import health
from app.main import app


@pytest.fixture
async def client():
    """Create async test client."""
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as ac:
        yield ac


@pytest.mark.asyncio
async def test_health_check(client: AsyncClient):
    """Test health check endpoint."""
    response = await client.get("/health")
    assert response.status_code == 200

    data = response.json()
    assert data["status"] == "healthy"
    assert "service" in data
    assert "version" in data


@pytest.mark.asyncio
async def test_readiness_check(client: AsyncClient, monkeypatch: pytest.MonkeyPatch):
    """Test readiness check endpoint."""
    async def ok_check(*_args, **_kwargs):
        return "ok"

    monkeypatch.setattr(health, "_check_database", ok_check)
    monkeypatch.setattr(health, "_check_redis", ok_check)
    monkeypatch.setattr(health, "_check_llm", ok_check)
    monkeypatch.setattr(health, "_check_rag_schema", ok_check)

    response = await client.get("/ready")
    assert response.status_code == 200

    data = response.json()
    assert data["status"] == "ready"
    assert "checks" in data
    assert data["checks"]["rag_schema"] == "ok"


@pytest.mark.asyncio
async def test_readiness_check_should_return_503_when_any_check_fails(
    client: AsyncClient,
    monkeypatch: pytest.MonkeyPatch,
):
    """Readiness should fail when dependency checks fail."""

    async def ok_check(*_args, **_kwargs):
        return "ok"

    async def failed_check(*_args, **_kwargs):
        return "error:unavailable"

    monkeypatch.setattr(health, "_check_database", ok_check)
    monkeypatch.setattr(health, "_check_redis", failed_check)
    monkeypatch.setattr(health, "_check_llm", ok_check)
    monkeypatch.setattr(health, "_check_rag_schema", ok_check)

    response = await client.get("/ready")
    assert response.status_code == 503

    data = response.json()
    assert data["status"] == "not_ready"
    assert data["checks"]["redis"] == "error:unavailable"


@pytest.mark.asyncio
async def test_readiness_check_should_return_503_when_rag_schema_not_ready(
    client: AsyncClient,
    monkeypatch: pytest.MonkeyPatch,
):
    """Readiness should fail when RAG schema is not yet created."""

    async def ok_check(*_args, **_kwargs):
        return "ok"

    async def schema_not_ready(*_args, **_kwargs):
        return "error:schema_not_ready"

    monkeypatch.setattr(health, "_check_database", ok_check)
    monkeypatch.setattr(health, "_check_redis", ok_check)
    monkeypatch.setattr(health, "_check_llm", ok_check)
    monkeypatch.setattr(health, "_check_rag_schema", schema_not_ready)

    response = await client.get("/ready")
    assert response.status_code == 503

    data = response.json()
    assert data["status"] == "not_ready"
    assert data["checks"]["rag_schema"] == "error:schema_not_ready"
