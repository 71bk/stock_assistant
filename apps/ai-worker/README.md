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

## License

MIT
