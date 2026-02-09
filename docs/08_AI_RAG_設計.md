# AI / RAG 設計

> 狀態：草稿

## LLM 使用情境

- 

## SSE 串流規格

- reportType 為 enum：INSTRUMENT / PORTFOLIO / GENERAL（AI 報告回應欄位）

- 

## Prompt 管理

- 

## Chat 記憶系統 Roadmap（v1–v3）

### v1（MVP）
- 對話列表 + 訊息列表為核心，不做摘要；上下文採「最近 N 則 + token budget」。
- SSE 串流只在完成時一次寫入 assistant 訊息（避免每 chunk 更新 DB）。
- system prompt 可視為一則 role=system 訊息（每對話固定），方便追溯與調整。
- 資料表：conversations / conversation_messages（role=user/assistant/system，status 可選）。
- API：/api/ai/conversations、/api/ai/conversations/{id}、/messages（SSE）。

### v1.1（已落地）Chat 工具補強
- 目標：降低「代詞問句」與「貼 URL 查價」失敗率，並保持 API 合約不變。
- 實作方式：在送 LLM 前由後端注入 `Tool Results`（instrument candidates / quote）。
- symbol 擷取規則（由高到低）：
  - `symbolKey`（含 URL query 的 `symbolKey=TW:XTAI:2330`）
  - 數字 ticker（`2330`）
  - 英文 token（如 `AAPL`）
  - 中文 token（先去掉停用詞再比對）
- quote 觸發條件：訊息命中 `app.ai.chat.quote-search.keywords`，且至少有一個 candidate。
- quote 工具輸出：
  - `tool_quote_available: true|false`
  - `tool_quote_error`（quote 失敗時）
- 回覆策略：`tool_quote_available=true` 時，模型不得宣稱無法提供即時/最新價格。
- 代詞 fallback：
  - 前提：本輪 `searchCandidates` 無結果，且含 `這隻/這檔/這支/那隻/那檔/那支/它/該股/他/她/這個/那個`。
  - 步驟 1：讀 `conversationLastMentioned` 快取（key=`{userId}:{conversationId}`）。
  - 步驟 2：快取 miss 時回看最近 user 訊息（預設 5，`app.ai.chat.pronoun-lookback.limit`）。
  - 命中後會更新 `lastMentionedSymbolKey`。
- 非代詞但報價意圖（命中 quote keywords）時：若本輪無候選，僅使用 `lastMentionedSymbolKey` 快取，不啟用回溯掃描。
- 可觀測性：quote tool 失敗時記錄 `symbolKey + reason` warning log（不再完全靜默）。

### v2（可用性升級）
- 摘要機制：超過 N 則時先摘要舊訊息，存 conversation.summary 或獨立 summary 表。
- 長期記憶：將摘要/重點寫入向量庫，與 RAG 共用檢索流程。
- 支援對話標題/分類/刪除，並提供記憶清理策略。

### v3（進階）
- 記憶分層：短期（最近訊息）/中期（摘要）/長期（向量檢索）。
- 多模型路由 + 工具呼叫（RAG/行情/檔案），加入安全策略多層提示。
- 個人化記憶（偏好/語氣），具可視化、可刪除、可審計。

## Chat 記憶系統資料表（v1）

### app.conversations
| 欄位 | 類型 | 說明 |
|------|------|------|
| id | BIGINT PK | 對話 ID |
| user_id | BIGINT FK | 使用者 ID |
| title | TEXT | 對話標題（v1 可用首句截斷） |
| prompt_version | TEXT | prompt 版本（便於追溯） |
| prompt_snapshot | TEXT | prompt 快照（便於 debug/回放） |
| summary | TEXT | 摘要（v2 預留） |
| created_at | TIMESTAMPTZ | 建立時間 |
| updated_at | TIMESTAMPTZ | 更新時間 |
| deleted_at | TIMESTAMPTZ | 軟刪（未落地，未來可加） |

索引建議：
- (user_id, updated_at DESC)

### app.conversation_messages
| 欄位 | 類型 | 說明 |
|------|------|------|
| id | BIGINT PK | 訊息 ID |
| conversation_id | BIGINT FK | 對話 ID |
| role | TEXT | system / user / assistant |
| content | TEXT | 訊息內容 |
| status | TEXT | PENDING/COMPLETED/FAILED（可選，用於 SSE） |
| client_message_id | TEXT | 客戶端訊息 ID（可選，用於去重） |
| created_at | TIMESTAMPTZ | 建立時間 |

索引建議：
- (conversation_id, id)
- UNIQUE (conversation_id, client_message_id)

行為約定：
- SSE 串流：先插入 user 訊息，assistant 訊息在串流結束時一次寫入（或先寫 PENDING，結束時更新）。
- 查詢上下文：取最近 N 則 + token budget，避免超過模型上限。
- client_message_id：前端可用 UUID 做冪等（避免重送造成重複訊息）。

## Chat 快取（Caffeine）

| Key | 類型 | 用途 |
|------|------|------|
| `conversationLastMentioned:{userId}:{conversationId}` | In-Memory（Caffeine） | 記錄該對話最近一次成功解析的 `symbolKey` |

- TTL：`app.ai.chat.last-mentioned.cache-ttl`（預設 `12h`）。
- 注意：此快取為 app instance local cache，不保證跨節點共享。

## Chunking/Embedding

## RAG 檔案 ingestion（物件儲存 + 防護）

**文件管理 (List/Delete) - v1.x 已實作**
- `GET /api/rag/documents`：分頁列出已上傳文件 (包含 UPLOAD 與 NOTE)。
- `DELETE /api/rag/documents/{id}`：刪除文件 (僅刪除 metadata，向量庫清理依賴後台機制)。

**短期防護（Backend）**
- 後端先檢查檔案大小（`app.rag.max-file-size-mb`，預設 50MB）。
- 超過直接回 `VALIDATION_ERROR`，避免 backend OOM。

**長期路徑（建議）**
- Frontend → Backend → MinIO/S3（presign upload）
- Backend 取得 presigned **下載** URL → 呼叫 ai-worker `/ingest/url`
- ai-worker 下載後解析（Backend 不再讀 bytes）
  - presigned **下載** URL 僅供後端內部使用（RAG ingestion），前端不需要取得

**ai-worker 下載護欄（必設）**
- `RAG_DOWNLOAD_TIMEOUT_CONNECT=3`
- `RAG_DOWNLOAD_TIMEOUT_READ=30`
- `RAG_DOWNLOAD_TIMEOUT_TOTAL=60`
- `RAG_DOWNLOAD_MAX_BYTES=52428800`

**PDF 解析護欄（必設）**
- `PDF_MAX_PAGES=50`（避免小檔卻超多頁）
- `PDF_MAX_TOTAL_PIXELS=200000000`（避免超高 DPI/超大頁）

**現況行為**
- provider=local：仍走 `loadBytes()`（只適合小檔）
- provider=s3/minio：改走 presigned URL → ai-worker 拉取

## Portfolio 快照向量化（排程 + 手動）

**排程**
- `app.rag.snapshot.enabled=true` 開啟
- `app.rag.snapshot.cron` 控制頻率（預設每天 02:30）

**手動**
- `POST /api/admin/rag/portfolio-snapshots`

**輸出內容**
- 基本資訊：日期 / 組合名稱 / 幣別
- 持倉清單：ticker、名稱、symbol_key、qty、avg_cost、currency
- `source_type=portfolio`，tags 會帶 `portfolioId:{id}`

### Provider 切換（Embedding / OCR）

**目標：** 允許在不改程式碼的前提下切換模型來源，並確保向量維度一致。

**環境變數（ai-worker）**
- `LLM_PROVIDER`：聊天 / OCR Vision 使用的 LLM（`gemini` / `openai` / `ollama`）
- `EMBEDDING_PROVIDER`：向量模型來源（`gemini` / `openai` / `ollama`）
- `OCR_PROVIDER`：OCR 來源（`auto` / `tesseract` / `gemini` / `openai` / `ollama`）
- `EMBEDDING_EXPECTED_DIMENSION`：向量維度的硬性檢查（**必填**）

**維度對應（常用）**
- `gemini-embedding-001` → 1536（需搭配 `output_dimensionality=1536`）
- `text-embedding-3-small` → 1536
- `text-embedding-3-large` → 3072
- `nomic-embed-text` → 768

**切換示例**
- 只換 Embedding（LLM/OCR 繼續 Gemini）：
  - `LLM_PROVIDER=gemini`
  - `EMBEDDING_PROVIDER=ollama`
  - `EMBEDDING_EXPECTED_DIMENSION=768`
- 只換 OCR（LLM 仍用 Gemini）：
  - `OCR_PROVIDER=tesseract`

**注意事項**
- `EMBEDDING_EXPECTED_DIMENSION` 與 DB `vector(n)` 維度必須一致，否則 ai-worker 會在啟動時 fail-fast。
- OCR 若使用 `auto`：先走 tesseract，低信心才 fallback vision。

## pgvector schema

- 

## Citations

- 

## 成本/速率/快取

- 
