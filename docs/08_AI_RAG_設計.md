# AI / RAG 設計

> 狀態：草稿

## LLM 使用情境

- 

## SSE 串流規格

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

## Chunking/Embedding

- 

## pgvector schema

- 

## Citations

- 

## 成本/速率/快取

- 
