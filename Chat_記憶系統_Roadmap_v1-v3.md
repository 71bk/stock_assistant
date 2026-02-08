# Chat 記憶系統 Roadmap（v1～v3）
> 目標：建立可擴展的對話記憶能力，從 v1 的多對話 + 最近 N 訊息開始，逐步加入摘要、RAG、persona、多模型路由與評估機制。
> v1 僅做最小可用版（MVP），避免過早引入摘要造成記憶扭曲。

---

## v1（MVP）：多對話 + 最近 N + SSE 一次寫入

### v1 目標
- 支援多對話列表與對話切換
- 每次回覆帶最近 N 則訊息（含 system）
- SSE 串流回覆期間不做每段更新，避免 DB 高頻寫入
- 保留後續擴展欄位（summary）

---

### A. DB（Flyway Migration：V10）
1. 新增 `app.conversations`
   - 欄位：`id, user_id, title, summary(NULL), created_at, updated_at`
   - Index：`(user_id, updated_at desc)`
   - `summary` 預留給 v2

2. 新增 `app.conversation_messages`
   - 欄位：`id, conversation_id, role(system|user|assistant), content, status(PENDING/COMPLETED/FAILED 可選), client_message_id(可選), created_at`
   - Index：`(conversation_id, id)`
   - Unique（可選）：`(conversation_id, client_message_id)`，避免重送

> v1 可先不做 DB trigger；在 service 內更新 `conversation.updated_at`

---

### B. Backend（Entity / Repository / Service）
#### 1) Entity + Repository
- `ConversationEntity` / `ConversationRepository`
- `ConversationMessageEntity` / `ConversationMessageRepository`

#### 2) Service（`AiConversationService`）
v1 行為：
- `createConversation(userId, optionalTitleOrSystemProfile)`
  - 建 conversation
  - 插入 system message（role=system）
- `listConversations(userId)`（v1 可先不分頁）
- `getConversationDetail(userId, conversationId, limitN)`
  - 回傳 conversation + 最近 N 則訊息（含 system）
- `appendUserMessage(userId, conversationId, content, clientMessageId)`
  - 檢查 conversation.user_id
  - 插入 user message
  - 更新 conversation.updated_at
- `appendAssistantMessage(conversationId, content, status)`
  - SSE 完成後一次寫入（或更新 placeholder）

#### 3) 組裝上下文
- `loadRecentMessages(conversationId, N, tokenBudget)`
  - 先取最近 50
  - 從尾往前累加直到 token budget
  - `messages = [system, ...selectedHistory, newUser]`

---

### C. Controller / API（/api/ai/conversations）
1. `POST /api/ai/conversations`
   - 建立新對話，回傳 `conversationId`

2. `GET /api/ai/conversations`
   - 列出對話（v1 可先不分頁）

3. `GET /api/ai/conversations/{id}`
   - 回傳對話 + 最近 N 則訊息

4. `POST /api/ai/conversations/{id}/messages`（SSE）
流程：
1) 驗證對話
2) `appendUserMessage(...)`
3) 插入 assistant placeholder（status=PENDING，可選）
4) 呼叫 LLM 串流
5) 串流期間累積 memory buffer（不寫 DB）
6) `onComplete`：插入/更新 assistant message（COMPLETED）
7) `onError`：更新為 FAILED + SSE error

SSE event：
- `meta`（conversationId / messageIds / requestId）
- `delta`
- `done`
- `error`

---

### D. Frontend
1) chat 開啟 → `POST /api/ai/conversations` 拿 `conversationId`
2) 發話 → `POST /api/ai/conversations/{id}/messages`（SSE）
   - 先 push user message
   - `delta` append 到 assistant bubble
3) 對話列表 / 切換
   - `GET /api/ai/conversations`
   - `GET /api/ai/conversations/{id}`

---

### E. v1 驗收清單
- 能建立多對話並切換
- SSE 正常串流，且 assistant 最終寫入 DB
- `clientMessageId` 可防重送
- Token budget 不超限（必要時減少歷史）

---

## v2：摘要記憶 + RAG + Persona

### v2 目標
- 長對話摘要（summary memory）
- 工具/檢索（RAG + 系統資料）
- Persona/profile
- 觀測指標（latency / tokens / SSE drop）

### A. 摘要記憶
- 新增欄位：
  - `summary_until_message_id`
  - `summary_updated_at`
- 當歷史超過 budget 時：
  - 產生摘要寫入 `conversations.summary`
  - 後續 context = `system + summary + recent`

### B. 工具 / RAG
1) 工具端點（後端）
- `getUserTrades(...)`
- `getUserPositions(...)`
- `getOcrDraft(...)`
- `searchPortfolioNotes(...)`

2) LLM tool/function calling
- 條件觸發工具查詢
- 限制 userId 範圍，避免跨用戶查詢

3) RAG
- chunk + embedding + pgvector 搜尋
- topK chunk 拼入 context

### C. Persona / Prompt Profile
- conversation 增加 `profile`（investment / ocr / general）
- system prompt 根據 profile 加載

### D. 觀測指標
- `model / tokens / latency / error_code`
- SSE drop rate
- requestId trace

---

## v3：多模型路由 + 長期記憶 + 安全評估

### v3 目標
- Router：依任務選模型（chat / summarise / retrieve）
- 長期記憶（user memory）
- Prompt injection / 安全策略
- 評估與 A/B 測試

### A. Router
- 任務分類
- 成本 / 延遲 / 失敗 fallback

### B. 長期記憶
1) `app.user_memories`
   - `id, user_id, scope, content, embedding(可選), source, confidence, created_at, updated_at, deleted_at`
2) `app.user_memory_candidates`
   - 需人工確認
3) 檢索
   - embedding + scope filter

### C. 安全策略
- system / memory / retrieved context 分層
- tool 呼叫審計
- prompt injection 防護

### D. 評估
- UI 回饋 + 標註
- A/B prompt
- RAG / summary / model quality 量測

---

## 實作順序建議
1) v1 DB + API + SSE 落地（端到端）
2) v1 驗收與修正
3) v2 摘要 / RAG / persona
4) v3 Router / 長期記憶 / 評估
