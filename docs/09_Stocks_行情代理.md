# Stocks 行情代理

> 狀態：已實作 (2026-02-06)

## 第三方供應商抽象（Port/Adapter）

系統採用 **Hexagonal Architecture (Ports and Adapters)** 模式，將外部行情來源隔離。

- **Port (Interface)**: `StockMarketClient`
  - `getQuote(ticker)`: 取得即時報價
  - `getCandles(ticker, interval, from, to)`: 取得 K 線資料
  - `getSupportedMarket()`: 回傳支援的市場 (TW, US)

- **Adapters (Implementation)**:
  - **FugleClient**: 負責台股 (TWSE, TPEx) 行情（透過 Fugle API）。
  - **AlpacaClient**: 負責美股 (NYSE, NASDAQ) 行情（透過 Alpaca Market Data API）。

## Quote/Candles 正規化

所有外部供應商回傳的資料，統一轉換為內部標準格式：

- **Quote**: `price`, `change`, `changePercent`, `volume`, `lastTradeTime`
- **Candle**: `open`, `high`, `low`, `close`, `volume`, `time`
- **時區處理**: 所有時間統一轉為 UTC 儲存與傳輸。

## Redis cache keys/TTL

為了降低第三方 API 費用與延遲，實作了 Redis 快取層：

| 資料類型 | Cache Key Pattern | 預設 TTL | 更新策略 |
|---------|-------------------|----------|----------|
| 即時報價 | `quote:{symbolKey}` | 1 分鐘 | 若快取存在直接回傳；過期則穿透查詢並回寫 |
| K 線資料 | `candles:{symbolKey}:{interval}:{from}:{to}` | 5 分鐘 | 歷史資料可設更長 TTL |

- **防擊穿機制 (Single Flight)**:
  - 使用 `ConcurrentHashMap<String, CompletableFuture>` (`inflight` map)確保同一時間對同一 `symbolKey` 只有一個請求會打到外部 API，其餘請求等待該結果。

## Rate limit

除了快取外，針對外部 API 實作了 **Rate Limiter** (基於 Token Bucket 或漏桶演算法，視 `app-common` 實作細節而定)，防止觸發第三方服務的 Rate Limit 配額，保障系統穩定性。

## Instrument master（symbol_key/aliases）

商品識別採用全域唯一的 **Symbol Key** 格式：

- 格式：`{MarketCode}:{ExchangeCode}:{Ticker}`
- 範例：
  - 美股蘋果：`US:XNAS:AAPL`
  - 台股台積電：`TW:XTAI:2330`
  - 台股 0050：`TW:XTAI:0050`
