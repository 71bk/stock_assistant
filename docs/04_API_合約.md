# API 合約

> 狀態：v0 草稿（可落地）  
> 範圍：MVP + v1（以標註說明）

---

## 命名規範
- **Base Path**：`/api`（目前不加 `/v1`，如需版本化由 Gateway 加前綴）
- **資源命名**：名詞複數（`portfolios` / `trades` / `stocks`）
- **動作型**：僅在必要時使用（如 `:id/confirm`）
- **ID 形式**：回傳與輸入皆使用 **字串**（避免 JS 精度問題；DB 可為 BIGINT/UUID）
- **時間/日期**：全部 **UTC**；`TIMESTAMP` 用 ISO 8601（例 `2026-01-07T12:34:56Z`），`DATE` 用 `YYYY-MM-DD`
- **金額/數量**：建議以 **字串**傳遞（DECIMAL），前端用 decimal library；若使用 number 需注意精度
- **DTO/VO 命名**：
  - **Request**：放置於 `dto` package，命名為 `*Request`（例 `CreateTradeRequest`）
  - **Response**：放置於 `vo` package，命名為 `*Response`（例 `TradeResponse`）
  - **使用 Lombok**：統一使用 `@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`
  - **好處**：團隊風格一致、支援 Builder Pattern、欄位調整維護成本低

---

## 通用規範
- **認證**：Access/Refresh 均為 HttpOnly Cookie  
  - `access_token`：短效  
  - `refresh_token`：長效，`Path=/api/auth/refresh`
- **Content-Type**：預設 `application/json`  
  - 檔案上傳：`multipart/form-data`  
  - SSE：`text/event-stream`
- **分頁**：`page`（1 起算）、`size`（預設 20）、`sort=field,desc`
- **Trace**：回傳 `traceId` 便於追查

---

## 回傳格式
### ApiResponse（JSON）
```json
{
  "success": true,
  "data": {},
  "error": null,
  "traceId": "c2f2c7d5b7b44b6a"
}
```

### ErrorResponse（JSON）
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "field 'price' is required",
    "details": { "field": "price" }
  },
  "traceId": "c2f2c7d5b7b44b6a"
}
```

### PageResponse（JSON）
```json
{
  "success": true,
  "data": {
    "items": [],
    "page": 1,
    "size": 20,
    "total": 0
  },
  "error": null,
  "traceId": "c2f2c7d5b7b44b6a"
}
```

> SSE 與檔案下載不包 ApiResponse。

---

## 錯誤碼與 HTTP 狀態
| HTTP | code | 說明 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | 參數格式錯誤 |
| 401 | `AUTH_UNAUTHORIZED` | 未登入/Token 失效 |
| 403 | `AUTH_FORBIDDEN` | 權限不足 |
| 404 | `NOT_FOUND` | 資源不存在 |
| 409 | `CONFLICT` | 重複或狀態衝突 |
| 422 | `OCR_PARSE_FAILED` | OCR 解析失敗 |
| 429 | `RATE_LIMITED` | 超過速率限制 |
| 500 | `INTERNAL_ERROR` | 未預期錯誤 |

---

## Auth
### Endpoints
| Method | Path | 說明 | Auth |
|---|---|---|---|
| GET | `/api/auth/google/login` | 302 導向 Google OAuth | 否 |
| GET | `/api/oauth2/authorization/{provider}` | OAuth2 ?????Spring Security ??? | ? |
| GET | `/api/login/oauth2/code/google` | OAuth ???Spring Security ?????? Cookie ????? | 否 |
| GET | `/api/login/oauth2/**` | OAuth2 ?????Spring Security ??? | ? |
| GET | `/api/auth/me` | 取得登入使用者資訊 | 是 |
| POST | `/api/auth/refresh` | 旋轉 refresh、刷新 access | 是（refresh cookie） |
| POST | `/api/auth/logout` | 登出並撤銷 refresh session | 是 |
| GET | `/api/auth/sessions` | ??????/?????v1??????? | 是 |
| DELETE | `/api/auth/sessions/{sessionId}` | ???? session?v1??????? | 是 |

### User Profile（`GET /api/auth/me`）
```json
{
  "success": true,
  "data": {
    "id": "123",
    "email": "demo@example.com",
    "displayName": "Demo",
    "pictureUrl": "https://...",
    "baseCurrency": "TWD",
    "displayTimezone": "Asia/Taipei"
  },
  "error": null,
  "traceId": "..."
}
```

---


## System / Health
### Endpoints
| Method | Path | ?? | Auth |
|---|---|---|---|
| GET | `/api/health` | ???????? | ? |
| GET | `/api/actuator/health` | ?????Actuator? | ? |

---

## Users / Settings
| Method | Path | 說明 | Auth |
|---|---|---|---|
| PATCH | `/api/users/me/settings` | 更新偏好設定 | 是 |

Request（`PATCH /api/users/me/settings`）
```json
{
  "baseCurrency": "TWD",
  "displayTimezone": "Asia/Taipei"
}
```

---

## Stocks / Instruments
### Endpoints
| Method | Path | 說明 | Auth |
|---|---|---|---|
| GET | `/api/stocks/markets` | 市場列表（TW/US） | 是 |
| GET | `/api/stocks/exchanges` | 交易所列表（依 market 篩選） | 是 |
| GET | `/api/stocks/instruments` | 搜尋股票（代號/名稱/別名） | 是 |
| GET | `/api/stocks/instruments/{instrumentId}` | 取得單一商品 | 是 |
| GET | `/api/stocks/quote` | 即時報價 | 是 |
| GET | `/api/stocks/candles` | K 線資料 | 是 |

#### 搜尋（`GET /api/stocks/instruments`）
Query：`q`、`market`、`exchange`、`limit`

Response（節錄）
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": "1001",
        "symbolKey": "US:XNAS:AAPL",
        "ticker": "AAPL",
        "nameZh": "蘋果",
        "nameEn": "Apple Inc.",
        "market": "US",
        "exchange": "XNAS",
        "currency": "USD"
      }
    ],
    "page": 1,
    "size": 20,
    "total": 1
  },
  "error": null,
  "traceId": "..."
}
```

#### Quote（`GET /api/stocks/quote`）
Query：`instrumentId` **或** `symbolKey`（擇一）

Response（節錄）
```json
{
  "success": true,
  "data": {
    "instrumentId": "1001",
    "symbolKey": "US:XNAS:AAPL",
    "price": "198.32",
    "change": "-1.20",
    "changePct": "-0.60",
    "tsUtc": "2026-01-07T12:00:01Z"
  },
  "error": null,
  "traceId": "..."
}
```

#### Candles（`GET /api/stocks/candles`）
Query：`instrumentId`/`symbolKey`、`interval`、`from`、`to`

---

## Portfolio / Trades / Positions
### Endpoints
| Method | Path | 說明 | Auth |
|---|---|---|---|
| GET | `/api/portfolios` | 取得投資組合列表 | 是 |
| POST | `/api/portfolios` | 新增投資組合 | 是 |
| GET | `/api/portfolios/{portfolioId}` | 取得投資組合 | 是 |
| GET | `/api/portfolios/{portfolioId}/positions` | 持倉列表 | 是 |
| GET | `/api/portfolios/{portfolioId}/trades` | 交易列表（可篩選） | 是 |
| POST | `/api/portfolios/{portfolioId}/trades` | 新增交易 | 是 |
| PATCH | `/api/trades/{tradeId}` | 更新交易 | 是 |
| DELETE | `/api/trades/{tradeId}` | 刪除交易 | 是 |

#### 新增交易（`POST /api/portfolios/{portfolioId}/trades`）
```json
{
  "instrumentId": "1001",
  "tradeDate": "2026-01-07",
  "side": "BUY",
  "quantity": "10",
  "price": "198.32",
  "currency": "USD",
  "fee": "1.5",
  "tax": "0"
}
```

Response（節錄）
```json
{
  "success": true,
  "data": {
    "tradeId": "9001"
  },
  "error": null,
  "traceId": "..."
}
```

---

## Files
### Endpoints
| Method | Path | 說明 | Auth |
|---|---|---|---|
| POST | `/api/files` | 上傳檔案（multipart） | 是 |
| GET | `/api/files/{fileId}` | 取得檔案 metadata | 是 |
| POST | `/api/files/presign` | ???? URL????? | 是 |

Response（`POST /api/files` 節錄）
```json
{
  "success": true,
  "data": {
    "fileId": "501",
    "sha256": "..."
  },
  "error": null,
  "traceId": "..."
}
```

---

## OCR
### Endpoints
| Method | Path | 說明 | Auth |
|---|---|---|---|
| POST | `/api/ocr/jobs` | 建立 OCR Job（以 fileId） | 是 |
| GET | `/api/ocr/jobs/{jobId}` | 查詢 Job 狀態 | 是 |
| GET | `/api/ocr/jobs/{jobId}/drafts` | 取得草稿交易 | 是 |
| PATCH | `/api/ocr/drafts/{draftId}` | 更新草稿交易 | 是 |
| POST | `/api/ocr/jobs/{jobId}/confirm` | 確認匯入 | 是 |

#### 建立 OCR Job（`POST /api/ocr/jobs`）
```json
{
  "fileId": "501",
  "portfolioId": "2001"
}
```

Response（節錄）
```json
{
  "success": true,
  "data": {
    "jobId": "8001",
    "statementId": "7001",
    "status": "QUEUED"
  },
  "error": null,
  "traceId": "..."
}
```

#### 草稿交易（`GET /api/ocr/jobs/{jobId}/drafts` 節錄）
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "draftId": "9101",
        "instrumentId": "1001",
        "rawTicker": "AAPL",
        "tradeDate": "2026-01-03",
        "side": "BUY",
        "quantity": "5",
        "price": "190.00",
        "currency": "USD",
        "warnings": ["DATE_FORMAT_GUESS"]
      }
    ]
  },
  "error": null,
  "traceId": "..."
}
```

---

## AI（SSE）
### Endpoints
| Method | Path | 說明 | Auth |
|---|---|---|---|
| POST | `/api/ai/analysis/stream` | AI 行情/持倉分析（SSE） | 是 |
| GET | `/api/ai/reports` | 報告列表（v1） | 是 |
| GET | `/api/ai/reports/{reportId}` | 報告詳情（v1） | 是 |

#### Request（`POST /api/ai/analysis/stream`）
```json
{
  "instrumentId": "1001",
  "prompt": "請摘要最近一個月的趨勢與風險點"
}
```

#### SSE Response（`text/event-stream`）
```
event: meta
data: {"requestId":"r-123","instrumentId":"1001"}

event: delta
data: {"text":"近一個月價格呈現..."}

event: delta
data: {"text":"風險點包含..."}

event: done
data: {"reportId":"3001"}
```

---

## RAG（v1）
### Endpoints
| Method | Path | 說明 | Auth |
|---|---|---|---|
| POST | `/api/rag/documents` | 建立文件（可含 rawText 或 fileId） | 是 |
| POST | `/api/rag/query` | RAG 問答 | 是 |

---

## 範例 Request/Response
### 1) 取得交易列表（`GET /api/portfolios/{portfolioId}/trades`）
Query：`from`、`to`、`page`、`size`

Response（節錄）
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "tradeId": "9001",
        "instrumentId": "1001",
        "tradeDate": "2026-01-07",
        "side": "BUY",
        "quantity": "10",
        "price": "198.32",
        "currency": "USD"
      }
    ],
    "page": 1,
    "size": 20,
    "total": 1
  },
  "error": null,
  "traceId": "..."
}
```

### 2) Rate limit（429）
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "RATE_LIMITED",
    "message": "Too many requests, please retry later."
  },
  "traceId": "..."
}
```
