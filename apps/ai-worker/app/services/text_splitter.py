"""Text splitting utilities for RAG ingestion."""

from __future__ import annotations


def chunk_text(
    text: str,
    chunk_size: int = 500,
    chunk_overlap: int = 50,
) -> list[dict]:
    """
    Split text into overlapping chunks.

    Args:
        text: Text to split
        chunk_size: Maximum characters per chunk
        chunk_overlap: Number of characters to overlap

    Returns:
        List of chunk dicts with content and metadata
    """
    if not text:
        return []

    normalized = text.strip()
    if not normalized:
        return []

    chunks: list[dict] = []
    start = 0
    chunk_index = 0
    length = len(normalized)

    while start < length:
        end = min(start + chunk_size, length)

        if end < length:
            window = normalized[start:end]
            candidates = [
                window.rfind("\n\n"),
                window.rfind("\n"),
                window.rfind("。"),
                window.rfind(". "),
                window.rfind("! "),
                window.rfind("? "),
                window.rfind("！"),
                window.rfind("？"),
                window.rfind("; "),
                window.rfind("；"),
            ]
            best_break = max(candidates)
            if best_break >= int(chunk_size * 0.6):
                end = start + best_break + 1

        chunk_content = normalized[start:end].strip()

        if chunk_content:
            chunks.append(
                {
                    "chunk_index": chunk_index,
                    "content": chunk_content,
                    "start_char": start,
                    "end_char": end,
                }
            )
            chunk_index += 1

        next_start = end - chunk_overlap
        start = max(next_start, start + 1)

    return chunks
