"""Startup/lifespan tests for ai-worker."""

from __future__ import annotations

from types import SimpleNamespace
from unittest.mock import Mock

import pytest

from app import main as app_main


def _settings(
    *,
    expected_dimension: int = 1536,
    embedding_dimension: int = 1536,
) -> SimpleNamespace:
    return SimpleNamespace(
        service_name="ai-worker",
        environment="test",
        log_level="INFO",
        embedding_dimension=embedding_dimension,
        ocr_concurrency=2,
        ingest_concurrency=5,
        resolve_expected_embedding_dimension=lambda: expected_dimension,
    )


def _fake_logger() -> tuple[SimpleNamespace, Mock, Mock]:
    info = Mock()
    warning = Mock()
    return SimpleNamespace(info=info, warning=warning), info, warning


@pytest.mark.asyncio
async def test_lifespan_should_tolerate_missing_rag_schema(monkeypatch: pytest.MonkeyPatch) -> None:
    logger, _info, warning = _fake_logger()
    monkeypatch.setattr(app_main, "get_settings", lambda: _settings())
    monkeypatch.setattr(app_main, "configure_stdlib_logging", lambda _level: None)
    monkeypatch.setattr(app_main, "logger", logger)
    monkeypatch.setattr(app_main, "init_ocr_limits", lambda _value: None)
    monkeypatch.setattr(app_main, "init_ingest_limits", lambda _value: None)

    async def fake_init_pool() -> None:
        return None

    async def fake_close_pool() -> None:
        return None

    async def fake_get_dimension(self) -> int | None:
        return None

    monkeypatch.setattr(app_main, "init_pool", fake_init_pool)
    monkeypatch.setattr(app_main, "close_pool", fake_close_pool)
    monkeypatch.setattr(app_main.RagRepository, "get_embedding_column_dimension", fake_get_dimension)

    async with app_main.lifespan(app_main.app):
        pass

    warning.assert_called_once()


@pytest.mark.asyncio
async def test_lifespan_should_fail_when_rag_schema_dimension_mismatches(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    logger, _info, _warning = _fake_logger()
    monkeypatch.setattr(app_main, "get_settings", lambda: _settings())
    monkeypatch.setattr(app_main, "configure_stdlib_logging", lambda _level: None)
    monkeypatch.setattr(app_main, "logger", logger)
    monkeypatch.setattr(app_main, "init_ocr_limits", lambda _value: None)
    monkeypatch.setattr(app_main, "init_ingest_limits", lambda _value: None)

    async def fake_init_pool() -> None:
        return None

    async def fake_close_pool() -> None:
        return None

    async def fake_get_dimension(self) -> int | None:
        return 768

    monkeypatch.setattr(app_main, "init_pool", fake_init_pool)
    monkeypatch.setattr(app_main, "close_pool", fake_close_pool)
    monkeypatch.setattr(app_main.RagRepository, "get_embedding_column_dimension", fake_get_dimension)

    with pytest.raises(RuntimeError, match=r"vector\(768\).+1536"):
        async with app_main.lifespan(app_main.app):
            pass
