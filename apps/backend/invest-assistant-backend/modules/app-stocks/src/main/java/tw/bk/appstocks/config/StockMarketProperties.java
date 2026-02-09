package tw.bk.appstocks.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 股票市場 API 配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "stock")
public class StockMarketProperties {

    private Alpaca alpaca = new Alpaca();
    private Fugle fugle = new Fugle();
    private Tpex tpex = new Tpex();
    private Twse twse = new Twse();
    private Cache cache = new Cache();
    private RateLimit rateLimit = new RateLimit();

    @Data
    public static class Alpaca {
        private String keyId;
        private String secretKey;
        private String baseUrl = "https://data.alpaca.markets";
    }

    @Data
    public static class Fugle {
        private String apiKey;
        private String baseUrl = "https://api.fugle.tw/marketdata/v1.0/stock/";
    }

    @Data
    public static class Tpex {
        private String baseUrl = "https://www.tpex.org.tw/openapi/v1";
    }

    @Data
    public static class Twse {
        private String isinUrl = "https://isin.twse.com.tw/isin/class_main.jsp?issuetype=4&market=2";
    }

    @Data
    public static class Cache {
        /**
         * Quote 快取 TTL（毫秒）
         */
        private long quoteTtl = 5000;

        /**
         * Candles 快取 TTL（毫秒）
         */
        private long candlesTtl = 3600000;
    }

    @Data
    public static class RateLimit {
        /**
         * Enable in-process rate limiting for external market APIs.
         */
        private boolean enabled = true;

        /**
         * Fixed window size in milliseconds.
         */
        private long windowMs = 1000;

        /**
         * Allowed requests per window for Alpaca calls.
         */
        private int alpacaLimit = 8;

        /**
         * Allowed requests per window for Fugle calls.
         */
        private int fugleLimit = 8;
    }
}
