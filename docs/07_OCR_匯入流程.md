# OCR 匯入流程

> 狀態：已實作 (2026-02-06)

## 核心流程 (Pipeline)

本系統採用 **非同步佇列** 架構處理 OCR 任務，確保高併發下的穩定性。

1. **上傳 (Upload)**: 使用者上傳 PDF/Image，計算 **SHA-256**。
2. **去重 (Dedupe)**: 檢查 SHA-256 是否已存在。若 `force=false` 且存在，直接回傳舊 Job ID；若 `force=true`，則建立新 Job。
3. **排程 (Enqueue)**: 建立 `OcrJob` (QUEUED) 與 `Statement` (DRAFT)，將 Job 推送至 Redis Stream (`ocr:jobs`)。
4. **解析 (Processing)**: **AI Worker** (Python) 透過 Redis Group 消費訊息，呼叫 LLM (Vision/Text) 進行解析。
5. **回寫 (Callback/Result)**: 解析結果 (JSON) 寫回 `app.statements`，並將交易明細轉存至 `app.statement_trades` (草稿表)。狀態更新為 `DONE`。

## Draft→Review→Confirm 機制

為了確保帳務正確，OCR 結果**不會直接匯入**正式交易紀錄，而是強制經過「檢視與確認」流程。

1. **Draft (草稿)**:
   - 解析後的資料存於 `statement_trades` 表。
   - 狀態：`DRAFT` (Statement)。
   - 使用者可在此階段修改內容（如修正錯誤的識別結果）或刪除單筆草稿。

2. **Review (檢視)**:
   - 前端顯示草稿列表，標示 **Warnings** (如：疑似重複交易、交割日早於成交日)。
   - 系統自動計算 `row_hash` 以識別草稿變更。

3. **Confirm (確認匯入)**:
   - API: `POST /api/ocr/jobs/{jobId}/confirm`
   - 支援 **部分匯入**：使用者可勾選特定草稿匯入。
   - **驗證邏輯**:
     - 檢查 `instrumentId` 是否有效。
     - 檢查 **重複交易** (Duplicate Check)：比對 `stock_trades` 是否已有相同 (Portfolio, Instrument, Date, Side, Qty, Price) 的紀錄。
   - **匯入動作**:
     - 將草稿轉為 `TradeCommand`，寫入 `stock_trades` (正式表) 與 `user_positions` (庫存表)。
     - **匯入成功後，物理刪除對應的草稿**。
   - **完成判定**:
     - 當該 Statement 下的所有草稿都已處理 (匯入或刪除) 完畢，Statement 狀態更新為 `CONFIRMED`。

## 重新解析 (Reparse) 與版本控制

- API: `POST /api/ocr/jobs/{jobId}/reparse`
- 行為：
  1. 將目前的 Statement 標記為 **SUPERSEDED** (已過時)，並記錄 `superseded_at`。
  2. 建立 **全新** 的 Statement (DRAFT) 與 Job (QUEUED)。
  3. 重新執行完整的 OCR 流程。
- 目的：保留歷史紀錄 (Audit Trail)，同時允許使用者針對同一檔案使用不同參數或新版 Prompt 重新解析。

## 錯誤處理與重試

- **Job Timeout**: `OcrService` 檢查 `maxRunningMinutes` (預設 30分)，若 Job 卡在 `RUNNING` 過久，視為失敗。
- **存活檢查**: 若發現舊 Job 為 `FAILED` 或意外卡在 `QUEUED`，再次上傳相同檔案時，會自動重置狀態並重新 Enqueue。
- **AI Worker 重試**: AI Worker 端針對 LLM Rate Limit 實作了指數退避 (Exponential Backoff) 重試機制。

## 資料落表設計

| 階段 | 表格 | 說明 |
|------|------|------|
| 原始檔 | `app.files` | 存放實體檔案路徑與雜湊值 |
| 任務 | `app.ocr_jobs` | 追蹤進度、狀態、錯誤訊息 |
| 批次 | `app.statements` | 匯入批次，存放原始 LLM JSON |
| 草稿 | `app.statement_trades` | 待確認的交易，含警告/錯誤資訊 |
| 正式 | `app.stock_trades` | 確認後的正式交易紀錄 |

