package tw.bk.appapi.stocks.vo;

import lombok.Builder;
import lombok.Data;
import tw.bk.apppersistence.entity.WarrantProfileEntity;

/**
 * Warrant profile response.
 */
@Data
@Builder
public class WarrantProfileResponse {
    private String underlyingSymbol;
    private String expiryDate;

    public static WarrantProfileResponse from(WarrantProfileEntity entity) {
        return WarrantProfileResponse.builder()
                .underlyingSymbol(entity.getUnderlyingSymbol())
                .expiryDate(entity.getExpiryDate() != null ? entity.getExpiryDate().toString() : null)
                .build();
    }
}
