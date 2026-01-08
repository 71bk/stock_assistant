package tw.bk.appcommon.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;

public final class MoneyFormatUtils {
    private MoneyFormatUtils() {
    }

    public static String format(BigDecimal amount) {
        return format(amount, 2);
    }

    public static String format(BigDecimal amount, int scale) {
        if (amount == null) {
            return null;
        }
        BigDecimal scaled = amount.setScale(scale, RoundingMode.HALF_UP);
        DecimalFormat formatter = new DecimalFormat();
        formatter.setGroupingUsed(false);
        formatter.setMinimumFractionDigits(scale);
        formatter.setMaximumFractionDigits(scale);
        return formatter.format(scaled);
    }
}
