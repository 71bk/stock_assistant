"""Contract tests for worker-side RAG schema usage."""

from __future__ import annotations

import ast
from pathlib import Path
import unittest


REPOSITORY_PATH = Path(__file__).resolve().parents[1] / "app" / "db" / "rag_repository.py"


def _load_repository_source() -> str:
    return REPOSITORY_PATH.read_text(encoding="utf-8")


def _load_insert_document_function() -> ast.AsyncFunctionDef:
    module = ast.parse(_load_repository_source())
    for node in module.body:
        if isinstance(node, ast.ClassDef) and node.name == "RagRepository":
            for class_node in node.body:
                if (
                    isinstance(class_node, ast.AsyncFunctionDef)
                    and class_node.name == "insert_document_with_chunks"
                ):
                    return class_node
    raise AssertionError("RagRepository.insert_document_with_chunks not found")


class RagSchemaContractTest(unittest.TestCase):
    def test_insert_document_with_chunks_signature_exposes_embedding_metadata(self) -> None:
        """Worker API should keep embedding metadata explicit in the contract."""
        function_node = _load_insert_document_function()
        argument_names = [argument.arg for argument in function_node.args.args]

        self.assertIn("embedding_model", argument_names)
        self.assertIn("embedding_version", argument_names)
        self.assertIn("dimensions", argument_names)

    def test_insert_document_with_chunks_sql_uses_embedding_metadata_columns(self) -> None:
        """Worker SQL must stay aligned with the Flyway-owned rag_chunks schema."""
        source = _load_repository_source()

        self.assertIn("INSERT INTO vector.rag_chunks", source)
        self.assertIn("embedding_model", source)
        self.assertIn("embedding_version", source)
        self.assertIn("dimensions", source)
        self.assertIn("VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)", source)

    def test_repository_exposes_db_embedding_dimension_query(self) -> None:
        """Worker repository should resolve the exact DB vector dimension."""
        source = _load_repository_source()

        self.assertIn("get_embedding_column_dimension", source)
        self.assertIn("format_type(a.atttypid, a.atttypmod)", source)
        self.assertIn("c.relname = 'rag_chunks'", source)
        self.assertIn("a.attname = 'embedding'", source)


if __name__ == "__main__":
    unittest.main()
