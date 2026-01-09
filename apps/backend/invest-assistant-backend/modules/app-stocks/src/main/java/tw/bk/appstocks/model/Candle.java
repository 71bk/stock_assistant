package tw.bk.appstocks.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * K 線資料模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Candle implements Serializable {

    /**
     * 股票代碼
     */
    private String ticker;

    /**
     * K 線時間
     */
    private LocalDateTime timestamp;

    /**
     * 開盤價
     */
    private BigDecimal open;

    /**
     * 最高價
     */
    private BigDecimal high;

    /**
     * 最低價
     */
    private BigDecimal low;

    /**
     * 收盤價
     */
    private BigDecimal close;

    /**
     * 成交量
     */
    private Long volume;
}
