"""Low-cardinality Prometheus metrics for AI provider usage."""

from __future__ import annotations

import re
from collections.abc import Mapping

from prometheus_client import Counter, Histogram

AI_TOKENS = Counter(
    "ai_tokens",
    "AI provider token usage",
    ["provider", "model", "operation", "type"],
)
AI_CALLS = Counter(
    "ai_calls",
    "AI provider calls",
    ["provider", "model", "operation", "success"],
)
AI_CALL_DURATION = Histogram(
    "ai_call_duration_seconds",
    "AI provider call duration",
    ["provider", "model", "operation"],
    buckets=(
        0.005,
        0.01,
        0.025,
        0.05,
        0.075,
        0.1,
        0.25,
        0.5,
        0.75,
        1.0,
        2.5,
        5.0,
        7.5,
        10.0,
        30.0,
        60.0,
        120.0,
        300.0,
        float("inf"),
    ),
)
OCR_FALLBACK = Counter(
    "ocr_fallback",
    "OCR pipeline fallback transitions",
    ["path"],
)
OCR_RETRY = Counter(
    "ocr_retry",
    "OCR retry or reparse requests",
    ["reason"],
)
EMBEDDING_RETRY = Counter(
    "embedding_retry",
    "Embedding retry attempts",
    ["provider", "model", "scope", "reason"],
)
EMBEDDING_BATCH_FALLBACK = Counter(
    "embedding_batch_fallback",
    "Embedding batch-size fallback transitions",
    ["provider", "model"],
)

_SAFE_LABEL = re.compile(r"[^a-z0-9._:/-]+")


def normalize_label(value: object) -> str:
    """Normalize configured labels and prevent accidental high-cardinality values."""
    text = str(value or "unknown").strip().lower()
    text = _SAFE_LABEL.sub("_", text)
    return text[:100] or "unknown"


def record_tokens(
    provider: str,
    model: str,
    operation: str,
    *,
    input_tokens: int = 0,
    output_tokens: int = 0,
    cached_tokens: int = 0,
) -> None:
    values = {
        "input": input_tokens,
        "output": output_tokens,
        "cached": cached_tokens,
    }
    labels = (
        normalize_label(provider),
        normalize_label(model),
        normalize_label(operation),
    )
    for token_type, count in values.items():
        if count and count > 0:
            AI_TOKENS.labels(*labels, token_type).inc(count)


def record_gemini_usage(
    provider: str,
    model: str,
    operation: str,
    usage: Mapping[str, object] | None,
) -> None:
    if not usage:
        return
    record_tokens(
        provider,
        model,
        operation,
        input_tokens=_as_int(usage.get("promptTokenCount")),
        output_tokens=_as_int(usage.get("candidatesTokenCount")),
        cached_tokens=_as_int(usage.get("cachedContentTokenCount")),
    )


def record_call(
    provider: str,
    model: str,
    operation: str,
    *,
    success: bool,
    duration_seconds: float,
) -> None:
    labels = (
        normalize_label(provider),
        normalize_label(model),
        normalize_label(operation),
    )
    AI_CALLS.labels(*labels, str(success).lower()).inc()
    AI_CALL_DURATION.labels(*labels).observe(max(0.0, duration_seconds))


def record_ocr_fallback(path: str) -> None:
    OCR_FALLBACK.labels(normalize_label(path)).inc()


def record_ocr_retry(reason: str) -> None:
    OCR_RETRY.labels(normalize_label(reason)).inc()


def record_embedding_retry(provider: str, model: str, scope: str, reason: str) -> None:
    EMBEDDING_RETRY.labels(
        normalize_label(provider),
        normalize_label(model),
        normalize_label(scope),
        normalize_label(reason),
    ).inc()


def record_embedding_batch_fallback(provider: str, model: str) -> None:
    EMBEDDING_BATCH_FALLBACK.labels(
        normalize_label(provider),
        normalize_label(model),
    ).inc()


def _as_int(value: object) -> int:
    try:
        return max(0, int(value or 0))
    except (TypeError, ValueError):
        return 0
