"""Tests for AI usage Prometheus metrics."""

from __future__ import annotations

from app.api.metrics import prometheus_metrics
from app.metrics import AI_TOKENS, normalize_label, record_gemini_usage


def test_normalize_label_should_bound_and_sanitize_values() -> None:
    assert normalize_label("Gemini 2.5 Flash") == "gemini_2.5_flash"
    assert normalize_label("") == "unknown"
    assert len(normalize_label("x" * 200)) == 100


def test_record_gemini_usage_should_split_token_types() -> None:
    labels = ("gemini", "test-model-metrics", "ocr_parse")
    before_input = AI_TOKENS.labels(*labels, "input")._value.get()
    before_output = AI_TOKENS.labels(*labels, "output")._value.get()
    before_cached = AI_TOKENS.labels(*labels, "cached")._value.get()

    record_gemini_usage(
        "gemini",
        "test-model-metrics",
        "ocr_parse",
        {
            "promptTokenCount": 120,
            "candidatesTokenCount": 30,
            "cachedContentTokenCount": 10,
        },
    )

    assert AI_TOKENS.labels(*labels, "input")._value.get() == before_input + 120
    assert AI_TOKENS.labels(*labels, "output")._value.get() == before_output + 30
    assert AI_TOKENS.labels(*labels, "cached")._value.get() == before_cached + 10


async def test_metrics_endpoint_should_export_ai_metrics() -> None:
    response = await prometheus_metrics()
    body = response.body.decode("utf-8")

    assert response.status_code == 200
    assert "ai_tokens_total" in body
    assert "ai_calls_total" in body
    assert "ai_call_duration_seconds" in body
