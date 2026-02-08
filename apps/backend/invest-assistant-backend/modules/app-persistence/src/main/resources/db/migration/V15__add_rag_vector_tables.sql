-- =====================================================
-- RAG 向量儲存系統
-- 新增 embedding metadata 欄位和表格中文說明
-- =====================================================

-- 建立 vector schema（若不存在）
CREATE SCHEMA IF NOT EXISTS vector;

-- 啟用 pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- =====================================================
-- RAG 文件表
-- =====================================================
CREATE TABLE IF NOT EXISTS vector.rag_documents (
    id              SERIAL PRIMARY KEY,
    user_id         INTEGER NOT NULL,
    source_type     VARCHAR(50) NOT NULL,
    source_id       VARCHAR(255),
    title           VARCHAR(500),
    meta            JSONB DEFAULT '{}',
    created_at      TIMESTAMP DEFAULT NOW()
);

COMMENT ON TABLE vector.rag_documents IS 'RAG 文件表 - 儲存使用者上傳的文件資訊';
COMMENT ON COLUMN vector.rag_documents.id IS '文件唯一識別碼';
COMMENT ON COLUMN vector.rag_documents.user_id IS '使用者 ID';
COMMENT ON COLUMN vector.rag_documents.source_type IS '來源類型（upload/note/ocr）';
COMMENT ON COLUMN vector.rag_documents.source_id IS '外部來源 ID（如 OCR Job ID）';
COMMENT ON COLUMN vector.rag_documents.title IS '文件標題';
COMMENT ON COLUMN vector.rag_documents.meta IS '額外 metadata（JSON 格式）';
COMMENT ON COLUMN vector.rag_documents.created_at IS '建立時間';

-- =====================================================
-- RAG 文字區塊表
-- =====================================================
CREATE TABLE IF NOT EXISTS vector.rag_chunks (
    id              SERIAL PRIMARY KEY,
    document_id     INTEGER NOT NULL REFERENCES vector.rag_documents(id) ON DELETE CASCADE,
    user_id         INTEGER NOT NULL,
    chunk_index     INTEGER NOT NULL,
    content         TEXT NOT NULL,
    embedding       vector(1536),
    meta            JSONB DEFAULT '{}',
    created_at      TIMESTAMP DEFAULT NOW()
);

-- 新增 embedding metadata 欄位（如果不存在）
ALTER TABLE vector.rag_chunks ADD COLUMN IF NOT EXISTS embedding_model VARCHAR(50);
ALTER TABLE vector.rag_chunks ADD COLUMN IF NOT EXISTS embedding_version VARCHAR(20) DEFAULT 'v1.0';
ALTER TABLE vector.rag_chunks ADD COLUMN IF NOT EXISTS dimensions INTEGER;

-- 表格說明
COMMENT ON TABLE vector.rag_chunks IS 'RAG 文字區塊表 - 儲存分割後的文字區塊和向量';
COMMENT ON COLUMN vector.rag_chunks.id IS '區塊唯一識別碼';
COMMENT ON COLUMN vector.rag_chunks.document_id IS '所屬文件 ID';
COMMENT ON COLUMN vector.rag_chunks.user_id IS '使用者 ID（冗餘加速查詢）';
COMMENT ON COLUMN vector.rag_chunks.chunk_index IS '區塊在文件中的順序索引';
COMMENT ON COLUMN vector.rag_chunks.content IS '區塊文字內容';
COMMENT ON COLUMN vector.rag_chunks.embedding IS '文字向量（1536 維）';
COMMENT ON COLUMN vector.rag_chunks.meta IS '區塊 metadata（起始位置等）';
COMMENT ON COLUMN vector.rag_chunks.embedding_model IS 'Embedding 模型名稱（如 gemini-embedding-001）';
COMMENT ON COLUMN vector.rag_chunks.embedding_version IS 'Embedding 版本號';
COMMENT ON COLUMN vector.rag_chunks.dimensions IS '向量維度';
COMMENT ON COLUMN vector.rag_chunks.created_at IS '建立時間';

-- =====================================================
-- 索引
-- =====================================================
-- 向量相似度搜尋索引（使用 IVFFlat 演算法）
CREATE INDEX IF NOT EXISTS idx_rag_chunks_embedding 
    ON vector.rag_chunks USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- 使用者查詢加速索引
CREATE INDEX IF NOT EXISTS idx_rag_chunks_user 
    ON vector.rag_chunks (user_id);

-- embedding model 篩選索引（支援模型切換）
CREATE INDEX IF NOT EXISTS idx_rag_chunks_embedding_model 
    ON vector.rag_chunks (embedding_model);
