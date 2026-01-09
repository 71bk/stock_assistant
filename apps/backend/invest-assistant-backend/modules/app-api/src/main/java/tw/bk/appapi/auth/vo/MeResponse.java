package tw.bk.appapi.auth.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.bk.apppersistence.entity.UserEntity;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeResponse {

    private String id;
    private String email;
    private String displayName;

    public static MeResponse from(UserEntity entity) {
        return MeResponse.builder()
                .id(String.valueOf(entity.getId()))
                .email(entity.getEmail())
                .displayName(entity.getDisplayName())
                .build();
    }
}
