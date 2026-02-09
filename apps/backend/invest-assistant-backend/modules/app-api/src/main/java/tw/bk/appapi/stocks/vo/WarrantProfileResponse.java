package tw.bk.appapi.stocks.vo;

import lombok.Builder;
import lombok.Data;
import tw.bk.appstocks.model.WarrantProfileView;

/**
 * Warrant profile response.
 */
@Data
@Builder
public class WarrantProfileResponse {
    private String underlyingSymbol;
    private String expiryDate;

    public static WarrantProfileResponse from(WarrantProfileView entity) {
        return WarrantProfileResponse.builder()
                .underlyingSymbol(entity.underlyingSymbol())
                .expiryDate(entity.expiryDate() != null ? entity.expiryDate().toString() : null)
                .build();
    }
}
