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

    private AlphaVantage alphaVantage = new AlphaVantage();
    private Fugle fugle = new Fugle();
    private Cache cache = new Cache();

    @Data
    public static class AlphaVantage {
        private String apiKey;
        private String baseUrl = "https://www.alphavantage.co/query";
    }

    @Data
    public static class Fugle {
        private String apiKey;
        private String baseUrl = "https://api.fugle.tw/realtime/v0.3";
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
