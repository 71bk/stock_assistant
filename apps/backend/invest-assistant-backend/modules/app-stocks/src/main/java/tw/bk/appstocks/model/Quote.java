package tw.bk.appstocks.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 股票報價資料模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Quote implements Serializable {

    /**
     * 股票代碼
     */
    private String ticker;

    /**
     * 現價
     */
    private BigDecimal price;

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
     * 昨收價
     */
    private BigDecimal previousClose;

    /**
     * 成交量
     */
    private Long volume;

    /**
     * 漲跌金額
     */
    private BigDecimal change;

    /**
     * 漲跌幅百分比
     */
    private BigDecimal changePercent;

    /**
     * 報價時間
     */
    private Instant timestamp;
}
