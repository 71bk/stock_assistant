package tw.bk.appapi.stocks.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * Instrument Detail Response (includes ETF profile if applicable)
 */
@Data
@Builder
public class InstrumentDetailResponse {

    @JsonProperty("instrument")
    private InstrumentResponse instrument;

    @JsonProperty("etf_profile")
    private EtfProfileResponse etfProfile;

    public static InstrumentDetailResponse of(InstrumentResponse instrument, EtfProfileResponse etfProfile) {
        return InstrumentDetailResponse.builder()
                .instrument(instrument)
                .etfProfile(etfProfile)
                .build();
    }
}
