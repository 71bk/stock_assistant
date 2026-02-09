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

    private String objectKey;

    private String uploadUrl;

    private String method;

    private Map<String, String> headers;

    public static PresignResponse from(PresignResult result) {
        return PresignResponse.builder()
                .fileId(result.fileId() != null ? result.fileId().toString() : null)
                .objectKey(result.objectKey())
                .uploadUrl(result.uploadUrl())
                .method(result.method())
                .headers(result.headers())
                .build();
    }
}
