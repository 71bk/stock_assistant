# ADR-0004 rag_schema_ownership

- Status: Accepted
- Date: 2026-04-24

## Context
- `vector.rag_documents` and `vector.rag_chunks` are created by backend Flyway migrations.
- `ai-worker` writes directly into these tables during RAG ingestion and reads them during retrieval.
- Java currently maps `vector.rag_documents`, but `vector.rag_chunks` is only exercised by worker SQL.
- `spring.jpa.hibernate.ddl-auto=none`, so Hibernate will not detect schema drift for these tables.

## Decision
- Backend Flyway migrations are the single source of truth for `vector.rag_documents` and `vector.rag_chunks`.
- `ai-worker` owns row mutation for `vector.rag_documents` and `vector.rag_chunks`.
- Java may read these tables for ownership checks and listing, but it must not directly insert or delete RAG rows.
- `ai-worker` may read and write these tables, but it must not define or evolve the schema independently.
- `ai-worker` startup must validate that its configured embedding dimension matches the actual
  `vector.rag_chunks.embedding` column dimension.
- Any RAG schema change must land in the same PR with:
  - a Flyway migration
  - a Java schema contract test update
  - a `docs/05_DB_資料庫設計.md` update
  - an `ai-worker` SQL and test update when worker queries or inserts are affected
- Java will not introduce a `RagChunkEntity` until there is a concrete Java-side use case that requires direct chunk reads or writes.

## Consequences
- Pros:
  - Schema ownership is explicit.
  - Drift between Flyway and worker SQL is caught earlier in CI.
  - The team avoids premature JPA mapping work around pgvector.
- Cons / Risks:
  - Two runtimes still depend on the same physical tables.
  - Contract tests add containerized test cost.
- Mitigations:
  - Keep schema contract tests narrow and fast.
  - Require schema, docs, and worker SQL changes to ship together.
