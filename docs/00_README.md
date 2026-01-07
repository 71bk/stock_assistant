# 文件導覽（docs/）

> 更新日期：2026-01-07  
> 目標：把「穩定不常改」的內容留在架構書，會一直變動的細節拆到各自文件，避免所有內容都塞在同一份。

---

## 讀文件順序（建議）
1. **01_專案架構書.md**：高層架構、模組責任、資料流、決策摘要（最穩定）
2. **02_技術棧.md**：技術選型總表（前後端/DB/Redis/OCR/AI/部署）
3. **03_使用者故事.md**：MVP / v1 / v2+ 範圍與驗收條件（對客戶最有用）
4. **04_API_合約.md**：API、錯誤碼、SSE 規格
5. **05_DB_資料庫設計.md**：ERD、表設計、索引、命名規範、資料流落表
6. **06_Auth_安全設計.md**：Cookie/JWT、Refresh Rotation、Redis session、CORS/CSRF
7. **07_OCR_匯入流程.md**：Draft→Review→Confirm、民國轉西元、去重/冪等
8. **08_AI_RAG_設計.md**：Prompt、Chunking/Embedding、pgvector、citations
9. **09_Stocks_行情代理.md**：第三方 API proxy、cache TTL、rate limit、symbol_key
10. **10_部署與基礎設施.md**：Docker Compose、Nginx、env、CI/CD
11. **11_可觀測性與日誌.md**：log/trace/metrics（v1 後補）
12. **12_後端開發規範.md**：模組依賴、DTO/VO、例外處理、交易邊界

---

## 文件清單（點了就開）
- [01_專案架構書.md](01_專案架構書.md)
- [02_技術棧.md](02_技術棧.md)
- [03_使用者故事.md](03_使用者故事.md)
- [04_API_合約.md](04_API_合約.md)
- [05_DB_資料庫設計.md](05_DB_資料庫設計.md)
- [06_Auth_安全設計.md](06_Auth_安全設計.md)
- [07_OCR_匯入流程.md](07_OCR_匯入流程.md)
- [08_AI_RAG_設計.md](08_AI_RAG_設計.md)
- [09_Stocks_行情代理.md](09_Stocks_行情代理.md)
- [10_部署與基礎設施.md](10_部署與基礎設施.md)
- [11_可觀測性與日誌.md](11_可觀測性與日誌.md)
- [12_後端開發規範.md](12_後端開發規範.md)

---

## ADR（Architecture Decision Record）— 決策紀錄
> 只要遇到「為什麼選這個、不選那個」就寫一篇 ADR。  
> 架構書只要引用 ADR 編號即可，避免重複改很多地方。

- 位置：`docs/adr/`
- 命名：`0001_主題.md`、`0002_主題.md`…

範例（你專案很常出現的決策）：
- Cookie-based JWT + Refresh Rotation（為什麼不用 localStorage）
- SSE vs WebSocket（為什麼先 SSE）
- PostgreSQL + JSONB + pgvector（為什麼不用 Mongo）
- OCR 是否要獨立 Python Worker

---

## Repo 建議放置（避免文件/腳本亂掉）
- **文件**：`/docs/**`
- **DDL / migration**
  - Flyway：`/db/migration/V1__init.sql`、`V2__add_xxx.sql`…
  - 或初始化：`/infra/postgres/init/001_init.sql`
- **Docker Compose / Nginx**
  - `/infra/docker-compose.yml`
  - `/infra/nginx/nginx.conf`

---

## 維護規則（很重要）
- `01_專案架構書.md`：只寫「高層」+「連結到其他文件」  
  不要塞表欄位、Redis key、Nginx 設定、程式碼片段。
- 需要新增規格 → **加到對應文件**，並在架構書補一行連結即可。
- 重大決策 → **寫 ADR**，架構書引用 ADR。

