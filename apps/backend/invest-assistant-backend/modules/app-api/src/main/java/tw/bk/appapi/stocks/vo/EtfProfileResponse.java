package tw.bk.appapi.stocks.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import tw.bk.apppersistence.entity.EtfProfileEntity;

/**
 * ETF Profile Response DTO
 */
@Data
@Builder
public class EtfProfileResponse {

    @JsonProperty("underlying_type")
    private String underlyingType;

    @JsonProperty("underlying_name")
    private String underlyingName;

    @JsonProperty("as_of_date")
    private String asOfDate;

    public static EtfProfileResponse from(EtfProfileEntity entity) {
        if (entity == null) {
            return null;
        }

        return EtfProfileResponse.builder()
                .underlyingType(entity.getUnderlyingType())
                .underlyingName(entity.getUnderlyingName())
                .asOfDate(entity.getAsOfDate() != null ? entity.getAsOfDate().toString() : null)
                .build();
    }
}
