package tw.bk.appapi.files.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.bk.appfiles.model.PresignResult;
import tw.bk.apppersistence.entity.FileEntity;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresignResponse {
    @JsonProperty("file_id")
    private String fileId;

    @JsonProperty("object_key")
    private String objectKey;

    @JsonProperty("upload_url")
    private String uploadUrl;

    @JsonProperty("method")
    private String method;

    @JsonProperty("headers")
    private Map<String, String> headers;

    public static PresignResponse from(PresignResult result) {
        FileEntity entity = result.file();
        return PresignResponse.builder()
                .fileId(entity.getId() != null ? entity.getId().toString() : null)
                .objectKey(entity.getObjectKey())
                .uploadUrl(result.uploadUrl())
                .method(result.method())
                .headers(result.headers())
                .build();
    }
}
