package tw.bk.appapi.ocr.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitOcrPasswordRequest {
    @NotBlank(message = "password is required")
    private String password;
}
