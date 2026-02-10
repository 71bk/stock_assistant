package tw.bk.appapi.files.vo;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.bk.appfiles.model.PresignResult;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresignResponse {
    private String fileId;

    private boolean alreadyExists;

    private String uploadUrl;

    private String method;

    private Map<String, String> headers;

    public static PresignResponse from(PresignResult result) {
        return PresignResponse.builder()
                .fileId(result.fileId() != null ? result.fileId().toString() : null)
                .alreadyExists(result.alreadyExists())
                .uploadUrl(result.uploadUrl())
                .method(result.method())
                .headers(result.headers())
                .build();
    }
}
