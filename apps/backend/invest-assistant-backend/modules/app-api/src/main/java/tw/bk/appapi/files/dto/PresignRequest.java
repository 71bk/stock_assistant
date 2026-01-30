package tw.bk.appapi.files.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresignRequest {
    @NotBlank(message = "sha256 is required")
    @JsonProperty("sha256")
    private String sha256;

    @NotNull(message = "size_bytes is required")
    @JsonProperty("size_bytes")
    private Long sizeBytes;

    @NotBlank(message = "content_type is required")
    @JsonProperty("content_type")
    private String contentType;
}
