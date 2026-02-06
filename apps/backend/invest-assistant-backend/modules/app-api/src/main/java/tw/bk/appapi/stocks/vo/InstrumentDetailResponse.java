package tw.bk.appapi.stocks.vo;

import lombok.Builder;
import lombok.Data;

/**
 * Instrument Detail Response (includes ETF/Warrant profile if applicable)
 */
@Data
@Builder
public class InstrumentDetailResponse {

    private InstrumentResponse instrument;

    private EtfProfileResponse etfProfile;

    private WarrantProfileResponse warrantProfile;

    public static InstrumentDetailResponse of(
            InstrumentResponse instrument,
            EtfProfileResponse etfProfile,
            WarrantProfileResponse warrantProfile) {
        return InstrumentDetailResponse.builder()
                .instrument(instrument)
                .etfProfile(etfProfile)
                .warrantProfile(warrantProfile)
                .build();
    }
}
