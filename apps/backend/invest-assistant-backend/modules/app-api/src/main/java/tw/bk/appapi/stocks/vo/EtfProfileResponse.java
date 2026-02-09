package tw.bk.appapi.stocks.vo;

import lombok.Builder;
import lombok.Data;
import tw.bk.appstocks.model.EtfProfileView;

/**
 * ETF Profile Response DTO
 */
@Data
@Builder
public class EtfProfileResponse {

    private String underlyingType;

    private String underlyingName;

    private String asOfDate;

    public static EtfProfileResponse from(EtfProfileView entity) {
        if (entity == null) {
            return null;
        }

        return EtfProfileResponse.builder()
                .underlyingType(entity.underlyingType())
                .underlyingName(entity.underlyingName())
                .asOfDate(entity.asOfDate() != null ? entity.asOfDate().toString() : null)
                .build();
    }
}
