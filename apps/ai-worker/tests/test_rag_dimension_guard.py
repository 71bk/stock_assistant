"""Tests for RAG embedding dimension guards."""

from __future__ import annotations

import unittest

from app.rag_schema_guard import (
    parse_vector_dimension,
    validate_configured_embedding_dimension,
    validate_db_embedding_dimension,
)


class RagDimensionGuardTest(unittest.TestCase):
    def test_parse_vector_dimension_should_extract_dimension(self) -> None:
        self.assertEqual(1536, parse_vector_dimension("vector(1536)"))
        self.assertEqual(768, parse_vector_dimension("VECTOR(768)"))
        self.assertIsNone(parse_vector_dimension("vector"))
        self.assertIsNone(parse_vector_dimension(None))

    def test_validate_configured_embedding_dimension_should_accept_matching_dimensions(self) -> None:
        validate_configured_embedding_dimension(1536, 1536)

    def test_validate_configured_embedding_dimension_should_fail_on_mismatch(self) -> None:
        with self.assertRaisesRegex(RuntimeError, "expected 3072 but got 1536"):
            validate_configured_embedding_dimension(3072, 1536)

    def test_validate_db_embedding_dimension_should_accept_matching_dimensions(self) -> None:
        self.assertTrue(validate_db_embedding_dimension(1536, 1536))

    def test_validate_db_embedding_dimension_should_return_false_when_schema_missing(self) -> None:
        """Schema not yet created by Flyway — return False, don't crash."""
        self.assertFalse(validate_db_embedding_dimension(1536, None))

    def test_validate_db_embedding_dimension_should_fail_on_mismatch(self) -> None:
        with self.assertRaisesRegex(RuntimeError, r"vector\(768\).+1536"):
            validate_db_embedding_dimension(1536, 768)


if __name__ == "__main__":
    unittest.main()
