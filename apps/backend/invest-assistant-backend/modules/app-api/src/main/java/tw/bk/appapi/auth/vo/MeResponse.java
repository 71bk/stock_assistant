package tw.bk.appapi.auth.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.bk.appauth.model.UserView;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeResponse {

    private String id;
    private String email;
    private String displayName;
    private String pictureUrl;
    private String baseCurrency;
    private String displayTimezone;

    public static MeResponse from(UserView entity, String baseCurrency, String displayTimezone) {
        return MeResponse.builder()
                .id(String.valueOf(entity.id()))
                .email(entity.email())
                .displayName(entity.displayName())
                .pictureUrl(entity.pictureUrl())
                .baseCurrency(baseCurrency)
                .displayTimezone(displayTimezone)
                .build();
    }
}
