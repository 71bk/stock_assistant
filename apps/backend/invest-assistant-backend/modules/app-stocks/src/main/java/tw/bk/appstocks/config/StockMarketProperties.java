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
    private Cache cache = new Cache();

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
}
