# AI Worker

投資助理平台的 AI 服務模組，負責 OCR 解析、RAG 文件處理與 LLM 分析。

## 功能

- **OCR 解析** (`POST /ocr`): 圖片/PDF → 結構化交易資料
- **RAG Ingestion** (`POST /ingest`): 文件 → chunk → embedding → pgvector
- **健康檢查** (`GET /health`): 服務狀態檢查

## 技術棧

- Python 3.11+
- FastAPI
- OpenAI API (GPT-4o / text-embedding-3-small)
- PostgreSQL + pgvector
- Redis (Queue)

## 快速開始

### 安裝依賴

```bash
# 使用 Poetry
poetry install

# 或使用 pip
pip install -e .
```

### 設定環境變數

```bash
cp .env.example .env
# 編輯 .env 填入必要的 API Keys
```

### 啟動服務

```bash
# 開發模式
poetry run uvicorn app.main:app --reload --port 8001

# 或
python -m uvicorn app.main:app --reload --port 8001
```

### API 文件

啟動後訪問：
- Swagger UI: http://localhost:8001/docs
- ReDoc: http://localhost:8001/redoc

## 專案結構

```
ai-worker/
├── app/
│   ├── main.py              # FastAPI 入口
│   ├── config.py            # 設定管理
│   ├── api/
│   │   ├── ocr.py           # OCR 端點
│   │   ├── ingest.py        # RAG ingestion 端點
│   │   └── health.py        # 健康檢查
│   ├── services/
│   │   ├── ocr_service.py   # OCR 解析邏輯
│   │   ├── llm_service.py   # LLM 呼叫
│   │   └── embedding_service.py
│   └── models/
│       └── schemas.py       # Pydantic 模型
├── tests/
├── pyproject.toml
├── Dockerfile
└── README.md
```

## 與 Java 後端整合

AI Worker 作為獨立服務，Java 後端透過 HTTP 呼叫：

```java
// 同步呼叫
POST http://ai-worker:8001/ocr
Content-Type: multipart/form-data

// 非同步（透過 Redis Queue）
XADD ocr:queue * file_id {id} user_id {uid}
```

## RAG Schema Ownership

- `vector.rag_documents`、`vector.rag_chunks` 的 schema source of truth 在 backend Flyway migration。
- AI Worker 擁有這兩張表的 row mutation，但不能自行定義或擴充 schema。
- Java 後端可以做 ownership check 與列表查詢，但不直接 insert/delete `vector.rag_documents`、`vector.rag_chunks`。
- AI Worker 啟動時會驗證 `vector.rag_chunks.embedding` 的 DB dimension 是否等於 `embedding_dimension`。
- 任何 RAG schema 變更都要同步更新：
  - backend Flyway migration
  - Java schema contract test
  - AI Worker SQL 與測試
  - `docs/05_DB_資料庫設計.md`
- 規則詳見 `docs/adr/0004_rag_schema_ownership.md`。

## 測試

```bash
# Worker 端 schema contract test
python -m unittest tests.test_rag_schema_contract
```

- `tests/test_rag_schema_contract.py` 會固定 `insert_document_with_chunks` 的函式參數與 `vector.rag_chunks` 寫入 SQL，避免 worker 端和 Flyway schema 漂移。

## License

MIT
