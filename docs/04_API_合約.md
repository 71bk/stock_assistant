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
- **LLM（AI 分析）**：Groq（OpenAI Compatible）
  - 預設模型：`openai/gpt-oss-120b`
  - 環境變數：
    - `APP_AI_GROQ_BASE_URL`（預設 `https://api.groq.com/openai/v1`）
    - `APP_AI_GROQ_API_KEY`
    - `APP_AI_GROQ_MODEL`（預設 `openai/gpt-oss-120b`）
    - `APP_AI_GROQ_TEMPERATURE`（預設 `0.2`）
    - `APP_AI_GROQ_MAX_TOKENS`（預設 `512`）
    - `APP_AI_GROQ_TOP_P`（預設 `1.0`）

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
| 501 | `NOT_IMPLEMENTED` | 功能尚未實作 |

---

## Admin
### Endpoints
| Method | Path | 說明 | Auth |
|---|---|---|---|
| POST | `/api/admin/instruments/sync` | 從 Fugle 同步 Instrument | 見下方 |

#### 認證方式
- 若 **未設定** `APP_ADMIN_API_KEY` → 只要登入即可
- 若 **已設定** `APP_ADMIN_API_KEY` → 必須帶 `X-Admin-Key` header

#### 同步 Instrument（`POST /api/admin/instruments/sync`）
Request Headers（若已設定 API Key）：
```
X-Admin-Key: your-secret-key
```

Response：
```json
{
  "success": true,
  "data": {
    "added": 150,
    "skipped": 2000
  },
  "error": null,
  "traceId": "..."
}
```
> 從 Fugle `/intraday/tickers` 匯入 TWSE/TPEx 的 EQUITY（含 ETF）。只新增不存在的 ticker。

---

## Auth
### Endpoints
| Method | Path | 說明 | Auth |
|---|---|---|---|
| GET | `/api/auth/google/login` | 302 導向 Google OAuth | 否 |
| GET | `/api/oauth2/authorization/{provider}` | OAuth2 授權端點（Spring Security 處理） | 否 |
| GET | `/api/login/oauth2/code/google` | OAuth 回調（Spring Security 處理，設置 Cookie 並重導） | 否 |
| GET | `/api/auth/me` | 取得登入使用者資訊 | 是 |
| POST | `/api/auth/refresh` | 旋轉 refresh、刷新 access | 是（refresh cookie） |
| POST | `/api/auth/logout` | 登出並撤銷 refresh session | 是 |

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
| Method | Path | 說明 | Auth |
|---|---|---|---|
| GET | `/api/health` | 基本健康檢查 | 否 |
| GET | `/api/actuator/health` | 健康檢查（Actuator） | 否 |

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

Response
```json
{
  "success": true,
  "data": {
    "baseCurrency": "TWD",
    "displayTimezone": "Asia/Taipei"
  },
  "error": null,
  "traceId": "..."
}
```

---

## Stocks / Instruments
### Endpoints
| Method | Path | 說明 | Auth |
|---|---|---|---|
| GET | `/api/stocks/markets` | 市場列表（TW/US） | 否 |
| GET | `/api/stocks/exchanges` | 交易所列表（可依 market 過濾） | 否 |
| GET | `/api/stocks/tickers` | 取得 tickers 清單（台股專用） | 否 |
| GET | `/api/stocks/instruments` | 搜尋商品（分頁/模糊查詢） | 否 |
| GET | `/api/stocks/instruments/{instrumentId}` | 取得單一商品 | 否 |
| GET | `/api/stocks/quote` | 即時報價 | 否 |
| GET | `/api/stocks/candles` | K 線資料 | 否 |
| GET | `/api/instruments` | 商品列表（分頁） | 否 |
| POST | `/api/instruments` | 手動建立商品 | 是 |
| GET | `/api/instruments/search` | 搜尋商品（自動補全） | 否 |
| GET | `/api/instruments/{symbolKey}` | 取得商品詳情 | 否 |

---

#### 市場列表（`GET /api/stocks/markets`）
Response：
```json
{
  "success": true,
  "data": [
    { "code": "US", "name": "美股" },
    { "code": "TW", "name": "台股" }
  ],
  "error": null,
  "traceId": "..."
}
```

---

#### 交易所列表（`GET /api/stocks/exchanges`）
Query Parameters：
- `market`（可選）

Response：
```json
{
  "success": true,
  "data": [
    { "code": "XNAS", "name": "NASDAQ", "market": "US" },
    { "code": "XNYS", "name": "New York Stock Exchange", "market": "US" },
    { "code": "XTAI", "name": "臺灣證券交易所", "market": "TW" }
  ],
  "error": null,
  "traceId": "..."
}
```

---

#### Tickers 清單（`GET /api/stocks/tickers`）
Query Parameters：
- `type`：EQUITY/INDEX/WARRANT/ODDLOT（必填）
- `exchange`：交易所代碼（可選）
- `market`：市場代碼（可選）
- `industry`：產業分類（可選）
- `isNormal`：正常交易標的（可選）
- `isAttention`：注意股（可選）
- `isDisposition`：處置股（可選）
- `isHalted`：暫停交易（可選）

Response 欄位說明：
- `date`：資料日期（YYYY-MM-DD）
- `type`：標的類型
- `exchange`：交易所
- `market`：市場
- `industry`：產業分類
- `isNormal` / `isAttention` / `isDisposition` / `isHalted`：交易狀態
- `data`：標的列表
  - `symbol`：代號
  - `name`：名稱

Response：
```json
{
  "success": true,
  "data": {
    "date": "2026-01-10",
    "type": "EQUITY",
    "exchange": "TWSE",
    "market": "TW",
    "industry": "Semiconductor",
    "isNormal": true,
    "isAttention": false,
    "isDisposition": false,
    "isHalted": false,
    "data": [
      { "symbol": "2330", "name": "台積電" },
      { "symbol": "2317", "name": "鴻海" }
    ]
  },
  "error": null,
  "traceId": "..."
}
```

---

#### 搜尋商品（`GET /api/stocks/instruments`）
Query Parameters：
- `q`：搜尋關鍵字（必填）
- `page`：頁碼（預設 1）
- `size`：每頁筆數（預設 20）

Response：
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "instrumentId": "1001",
        "symbolKey": "US:XNAS:AAPL",
        "ticker": "AAPL",
        "nameZh": "蘋果",
        "nameEn": "Apple Inc.",
        "market": "US",
        "exchange": "XNAS",
        "currency": "USD",
        "assetType": "STOCK"
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

---

#### 單一商品（`GET /api/stocks/instruments/{instrumentId}`）
Response：
```json
{
  "success": true,
  "data": {
    "instrumentId": "1001",
    "symbolKey": "US:XNAS:AAPL",
    "ticker": "AAPL",
    "nameZh": "蘋果",
    "nameEn": "Apple Inc.",
    "market": "US",
    "exchange": "XNAS",
    "currency": "USD",
    "assetType": "STOCK"
  },
  "error": null,
  "traceId": "..."
}
```

---

#### Quote（`GET /api/stocks/quote`）
Query Parameters：`instrumentId` 或 `symbolKey` 擇一

Response：
```json
{
  "success": true,
  "data": {
    "instrumentId": "1001",
    "symbolKey": "US:XNAS:AAPL",
    "price": "192.12",
    "open": "191.50",
    "high": "193.00",
    "low": "190.80",
    "previousClose": "192.47",
    "volume": 45678900,
    "change": "-0.35",
    "changePercent": "-0.18",
    "timestamp": "2026-01-29T10:20:30Z"
  },
  "error": null,
  "traceId": "..."
}
```

---

#### Candles（`GET /api/stocks/candles`）
Query Parameters：`instrumentId` 或 `symbolKey` 擇一，`interval`、`from`、`to`

Response：
```json
{
  "success": true,
  "data": [
    {
      "timestamp": "2026-01-01T00:00:00",
      "open": 100.1,
      "high": 101.5,
      "low": 99.9,
      "close": 101.0,
      "volume": 1234567
    }
  ],
  "error": null,
  "traceId": "..."
}
```

---

#### 商品列表（`GET /api/instruments`）
Query Parameters：
- `page`：頁碼（預設 1）
- `size`：每頁筆數（預設 20）

Response：`Result<PageResponse<InstrumentResponse>>`
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "instrumentId": "1001",
        "symbolKey": "US:XNAS:AAPL",
        "ticker": "AAPL",
        "nameZh": "蘋果",
        "nameEn": "Apple Inc.",
        "market": "US",
        "exchange": "XNAS",
        "currency": "USD",
        "assetType": "STOCK"
      }
    ],
    "page": 1,
    "size": 20,
    "total": 150
  },
  "error": null,
  "traceId": "..."
}
```

---

#### 手動建立商品（`POST /api/instruments`）
Request：
```json
{
  "ticker": "OLD123",
  "nameZh": "已下市公司",
  "nameEn": "Delisted Company",
  "market": "TW",
  "exchange": "TWSE",
  "currency": "TWD",
  "assetType": "STOCK"
}
```

| 欄位 | 必填 | 說明 |
|------|------|------|
| ticker | ✅ | 股票代碼 |
| nameZh | - | 中文名稱 |
| nameEn | - | 英文名稱 |
| market | ✅ | 市場代碼（TW/US） |
| exchange | ✅ | 交易所代碼（TWSE/TPEx/XNAS 等） |
| currency | ✅ | 幣別（TWD/USD） |
| assetType | - | 類型，預設 STOCK |

Response：
```json
{
  "success": true,
  "data": {
    "instrumentId": "12345",
    "symbolKey": "TW:TWSE:OLD123",
    "ticker": "OLD123",
    "nameZh": "已下市公司",
    "nameEn": "Delisted Company",
    "market": "TW",
    "exchange": "TWSE",
    "currency": "TWD",
    "assetType": "STOCK"
  },
  "error": null,
  "traceId": "..."
}
```

> **Note**: 若商品已存在（相同 symbol_key），會回傳 409 CONFLICT 錯誤。

---

#### 搜尋商品（`GET /api/instruments/search`）
Query Parameters：
- `q`：搜尋關鍵字（必填）
- `limit`：回傳數量（預設 10）

Response：
```json
{
  "success": true,
  "data": [
    {
      "instrumentId": "1001",
      "symbolKey": "US:XNAS:AAPL",
      "ticker": "AAPL",
      "nameZh": "蘋果",
      "nameEn": "Apple Inc.",
      "market": "US",
      "exchange": "XNAS",
      "currency": "USD",
      "assetType": "STOCK"
    }
  ],
  "error": null,
  "traceId": "..."
}
```

---

#### 商品詳情（`GET /api/instruments/{symbolKey}`）
Response：`Result<InstrumentDetailResponse>`
```json
{
  "success": true,
  "data": {
    "instrument": {
      "instrumentId": "1001",
      "symbolKey": "US:XNAS:AAPL",
      "ticker": "AAPL",
      "nameZh": "蘋果",
      "nameEn": "Apple Inc.",
      "market": "US",
      "exchange": "XNAS",
      "currency": "USD",
      "assetType": "STOCK"
    },
    "etfProfile": null
  },
  "error": null,
  "traceId": "..."
}
```

---


## Portfolio / Trades / Positions
### Endpoints
| Method | Path | 說明 | Auth |
|---|---|---|---|
| GET | `/api/portfolios` | 取得投資組合列表 | 是 |
| POST | `/api/portfolios` | 新增投資組合 | 是 |
| GET | `/api/portfolios/{portfolioId}` | 取得投資組合（含統計） | 是 |
| GET | `/api/portfolios/{portfolioId}/positions` | 持倉列表 | 是 |
| GET | `/api/portfolios/{portfolioId}/trades` | 交易列表（可篩選） | 是 |
| POST | `/api/portfolios/{portfolioId}/trades` | 新增交易 | 是 |
| PATCH | `/api/trades/{tradeId}` | 更新交易 | 是 |
| DELETE | `/api/trades/{tradeId}` | 刪除交易 | 是 |

#### 取得投資組合（`GET /api/portfolios/{portfolioId}`）
Response 欄位說明：
- `id`：投資組合 ID
- `name`：名稱
- `baseCurrency`：基礎幣別
- `totalMarketValue`：總市值（所有持倉現價總和）
- `totalCost`：總成本（所有持倉成本總和）
- `totalPnl`：總損益（總市值 - 總成本）
- `totalPnlPercent`：總損益率 %

Response：
```json
{
  "success": true,
  "data": {
    "id": "1",
    "name": "Main",
    "baseCurrency": "TWD",
    "totalMarketValue": 150000,
    "totalCost": 120000,
    "totalPnl": 30000,
    "totalPnlPercent": 25.0
  },
  "error": null,
  "traceId": "..."
}
```

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

#### 交易列表（`GET /api/portfolios/{portfolioId}/trades`）
Query Parameters：
- `from`：起始日期（YYYY-MM-DD，可選）
- `to`：結束日期（YYYY-MM-DD，可選）
- `page`：頁碼（預設 1）
- `size`：每頁筆數（預設 20）
- `sort`：排序方式（預設 `tradeDate,desc`）

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

---

## Files
### Endpoints
| Method | Path | 說明 | Auth |
|---|---|---|---|
| POST | `/api/files` | 上傳檔案（multipart） | 是 |
| GET | `/api/files/{fileId}` | 取得檔案 metadata | 是 |
| POST | `/api/files/presign` | 取得預簽 URL（尚未支援） | 是 |

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
| DELETE | `/api/ocr/drafts/{draftId}` | 刪除草稿交易 | 是 |
| POST | `/api/ocr/jobs/{jobId}/confirm` | 確認匯入（支援部分匯入） | 是 |

#### 建立 OCR Job（`POST /api/ocr/jobs`）
```json
{
  "fileId": "501",
  "portfolioId": "2001",
  "force": false
}
```
> **Note**: `force` 為可選參數，預設 `false`。設為 `true` 時會強制重新處理，忽略去重邏輯。

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
        "name": "Apple Inc.",
        "tradeDate": "2026-01-03",
        "settlementDate": "2026-01-05",
        "side": "BUY",
        "quantity": "5",
        "price": "190.00",
        "currency": "USD",
        "fee": "1.00",
        "tax": "0",
        "warnings": ["DATE_FORMAT_GUESS"],
        "errors": [],
        "rowHash": "b7b7f1c3d90a6c3f0d1a2f0e3b9d9c1d2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d"
      }
    ]
  },
  "error": null,
  "traceId": "..."
}
```

#### 更新草稿交易（`PATCH /api/ocr/drafts/{draftId}`）
```json
{
  "instrumentId": "1001",
  "rawTicker": "AAPL",
  "name": "Apple Inc.",
  "tradeDate": "2026-01-03",
  "settlementDate": "2026-01-05",
  "side": "BUY",
  "quantity": "5",
  "price": "190.00",
  "currency": "USD",
  "fee": "1.00",
  "tax": "0"
}
```

#### 刪除草稿交易（`DELETE /api/ocr/drafts/{draftId}`）
刪除單一草稿交易，不會匯入至正式交易表。

Response：
```json
{
  "success": true,
  "data": null,
  "error": null,
  "traceId": "..."
}
```

> **Note**: 當所有草稿都被刪除或匯入後，Job 狀態會自動更新為 `DONE`。

#### 確認匯入（`POST /api/ocr/jobs/{jobId}/confirm`）
支援**部分匯入**：只匯入指定的草稿，保留其他草稿供後續處理。

Request（可選）：
```json
{
  "draftIds": ["9101", "9102"]
}
```

| 情境 | Request Body | 行為 |
|------|--------------|------|
| 匯入全部 | `null` 或 `{}` | 匯入所有草稿（向後相容） |
| 部分匯入 | `{"draftIds": ["9101", "9102"]}` | 只匯入指定的草稿，其他保留 |

Response：
```json
{
  "success": true,
  "data": {
    "importedCount": 2,
    "errors": [
      {"draftId": "9103", "reason": "缺少股票代碼 (instrumentId)"},
      {"draftId": "9104", "reason": "重複交易（相同股票、日期、買賣、數量、價格）"}
    ]
  },
  "error": null,
  "traceId": "..."
}
```

**errors 陣列**：列出無法匯入的草稿及原因
- `缺少股票代碼 (instrumentId)`：草稿沒有對應的股票
- `重複交易（相同股票、日期、買賣、數量、價格）`：已存在相同的交易記錄

> **Note**: 已成功匯入的草稿會被刪除。有錯誤的草稿會保留在列表中，供使用者修正或刪除。


---

## AI（SSE）
### Endpoints
| Method | Path | 說明 | Auth |
|---|---|---|---|
| POST | `/api/ai/analysis/stream` | AI 行情/持倉分析（SSE） | 是 |
| GET | `/api/ai/reports` | 報告列表 | 是 |
| GET | `/api/ai/reports/{reportId}` | 報告詳情 | 是 |

#### Request（`POST /api/ai/analysis/stream`）
```json
{
  "portfolioId": "2001",
  "instrumentId": "1001",
  "reportType": "INSTRUMENT",
  "prompt": "請摘要最近一個月的趨勢與風險點"
}
```
> **Note**: `portfolioId` 與 `instrumentId` 擇一填寫，`reportType` 可為 `INSTRUMENT`、`PORTFOLIO`、`GENERAL`

#### SSE Response（`text/event-stream`）
```
event: meta
data: {"requestId":"r-123","instrumentId":"1001","reportType":"INSTRUMENT"}

event: delta
data: {"text":"近一個月價格呈現..."}

event: delta
data: {"text":"風險點包含..."}

event: done
data: {"reportId":"3001"}
```

#### SSE Error（未授權或內部錯誤時）
```
event: error
data: {"code":"AUTH_UNAUTHORIZED","message":"Unauthorized"}
```

#### 報告列表（`GET /api/ai/reports`）
Query Parameters：
- `page`（預設 1）
- `size`（預設 20，最大 100）

Response：
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "reportId": "3001",
        "reportType": "INSTRUMENT",
        "portfolioId": null,
        "instrumentId": "1001",
        "createdAt": "2026-01-30T12:00:00Z"
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

#### 報告詳情（`GET /api/ai/reports/{reportId}`）
Response：
```json
{
  "success": true,
  "data": {
    "reportId": "3001",
    "reportType": "INSTRUMENT",
    "portfolioId": null,
    "instrumentId": "1001",
    "inputSummary": "...",
    "outputText": "近一個月價格呈現上漲趨勢...",
    "createdAt": "2026-01-30T12:00:00Z"
  },
  "error": null,
  "traceId": "..."
}
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
### 1) Rate limit（429）
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "RATE_LIMITED",
    "message": "行情供應商限流，請稍後再試。",
    "details": {
      "vendor": "Fugle",
      "ticker": "2330",
      "retryAfter": "60"
    }
  },
  "traceId": "..."
}
```
