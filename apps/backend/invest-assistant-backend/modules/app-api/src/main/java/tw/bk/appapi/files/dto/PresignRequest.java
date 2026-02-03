package tw.bk.appapi.files.dto;

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
    private String sha256;

    @NotNull(message = "sizeBytes is required")
    private Long sizeBytes;

    @NotBlank(message = "contentType is required")
    private String contentType;
}
