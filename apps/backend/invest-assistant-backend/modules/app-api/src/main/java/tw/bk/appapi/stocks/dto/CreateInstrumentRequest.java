package tw.bk.appapi.stocks.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request DTO for creating a new instrument manually.
 */
@Data
public class CreateInstrumentRequest {

    @NotBlank(message = "ticker is required")
    private String ticker;

    private String nameZh;

    private String nameEn;

    @NotBlank(message = "market is required (e.g. TW, US)")
    private String market;

    @NotBlank(message = "exchange is required (e.g. TWSE, TPEx, XNAS)")
    private String exchange;

    @NotBlank(message = "currency is required (e.g. TWD, USD)")
    private String currency;

    /** Asset type: STOCK, ETF, BOND, etc. Default: STOCK */
    private String assetType = "STOCK";
}
