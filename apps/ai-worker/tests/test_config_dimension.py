"""Tests for Settings.resolve_expected_embedding_dimension."""

from __future__ import annotations

import unittest
from unittest.mock import patch

from app.config import EmbeddingProvider, Settings


class ResolveExpectedEmbeddingDimensionTest(unittest.TestCase):
    """Verify dimension resolution logic per provider."""

    def _make_settings(self, **overrides) -> Settings:
        """Create a Settings instance with overrides (bypass .env)."""
        defaults = {
            "embedding_dimension": 1536,
            "embedding_expected_dimension": None,
            "embedding_provider": None,
            "llm_provider": "openai",
            "openai_embedding_model": "text-embedding-3-small",
        }
        defaults.update(overrides)
        with patch.object(Settings, "model_config", {}):
            return Settings(**defaults)

    # ── Gemini ──────────────────────────────────────────────

    def test_gemini_returns_configured_embedding_dimension(self) -> None:
        """Gemini uses output_dimensionality, so expected == configured."""
        s = self._make_settings(
            embedding_provider="gemini",
            embedding_dimension=768,
        )
        self.assertEqual(768, s.resolve_expected_embedding_dimension())

    def test_gemini_with_custom_dimension(self) -> None:
        s = self._make_settings(
            embedding_provider="gemini",
            embedding_dimension=256,
        )
        self.assertEqual(256, s.resolve_expected_embedding_dimension())

    # ── OpenAI fixed table ──────────────────────────────────

    def test_openai_small_returns_1536(self) -> None:
        s = self._make_settings(
            embedding_provider="openai",
            openai_embedding_model="text-embedding-3-small",
        )
        self.assertEqual(1536, s.resolve_expected_embedding_dimension())

    def test_openai_large_returns_3072(self) -> None:
        s = self._make_settings(
            embedding_provider="openai",
            openai_embedding_model="text-embedding-3-large",
        )
        self.assertEqual(3072, s.resolve_expected_embedding_dimension())

    # ── Ollama fixed table ──────────────────────────────────

    def test_ollama_nomic_returns_768(self) -> None:
        s = self._make_settings(
            embedding_provider="ollama",
            ollama_embedding_model="nomic-embed-text",
        )
        self.assertEqual(768, s.resolve_expected_embedding_dimension())

    # ── Unknown model ───────────────────────────────────────

    def test_unknown_model_returns_none(self) -> None:
        s = self._make_settings(
            embedding_provider="openai",
            openai_embedding_model="some-future-model",
        )
        self.assertIsNone(s.resolve_expected_embedding_dimension())

    # ── Explicit override ───────────────────────────────────

    def test_explicit_override_takes_priority(self) -> None:
        """EMBEDDING_EXPECTED_DIMENSION beats all other resolution."""
        s = self._make_settings(
            embedding_provider="openai",
            openai_embedding_model="text-embedding-3-small",
            embedding_expected_dimension=999,
        )
        self.assertEqual(999, s.resolve_expected_embedding_dimension())

    def test_explicit_override_beats_gemini(self) -> None:
        s = self._make_settings(
            embedding_provider="gemini",
            embedding_dimension=768,
            embedding_expected_dimension=512,
        )
        self.assertEqual(512, s.resolve_expected_embedding_dimension())


if __name__ == "__main__":
    unittest.main()
