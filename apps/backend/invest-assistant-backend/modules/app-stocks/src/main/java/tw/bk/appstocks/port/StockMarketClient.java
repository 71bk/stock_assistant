package tw.bk.appstocks.port;

import tw.bk.appcommon.model.MarketCode;
import tw.bk.appstocks.model.Candle;
import tw.bk.appstocks.model.Quote;
import tw.bk.appstocks.model.TickerList;
import tw.bk.appstocks.model.TickerQuery;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 股票市場資料提供者介面（Port）
 * 透過 Adapter 實作不同的資料來源（Alpha Vantage, Fugle 等）
 */
public interface StockMarketClient {

    /**
     * 取得即時報價
     * 
     * @param ticker 股票代碼（如 AAPL, 2330）
     * @return 報價資料
     */
    Optional<Quote> getQuote(String ticker);

    /**
     * 取得 K 線資料
     * 
     * @param ticker   股票代碼
     * @param interval 時間間隔（1d, 1h, 5m 等）
     * @param from     起始日期
     * @param to       結束日期
     * @return K 線列表
     */
    List<Candle> getCandles(String ticker, String interval, LocalDate from, LocalDate to);

    /**
     * 此 Client 支援的市場代碼
     * 
     * @return 市場代碼（如 US, TW）
     */
    MarketCode getSupportedMarket();

    /**
     * 取得股票或指數列表（依條件查詢）
     *
     * @param query 查詢條件
     * @return ticker 列表
     */
    default Optional<TickerList> getTickers(TickerQuery query) {
        return Optional.empty();
    }
}
