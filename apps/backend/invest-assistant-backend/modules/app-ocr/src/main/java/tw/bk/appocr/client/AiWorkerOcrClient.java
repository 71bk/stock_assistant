package tw.bk.appocr.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.apppersistence.entity.FileEntity;

/**
 * Client for AI Worker OCR service.
 */
@Component
public class AiWorkerOcrClient {

    private final AiWorkerProperties properties;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public AiWorkerOcrClient(
            AiWorkerProperties properties,
            @Qualifier("aiWorkerOcrWebClient") WebClient webClient,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Process a file through AI Worker OCR endpoint.
     *
     * @param userId  User ID for tracking
     * @param file    File entity with metadata
     * @param content File bytes
     * @return OCR response with parsed trades
     */
    public AiWorkerOcrResponse processFile(Long userId, FileEntity file, byte[] content) {
        return processFile(userId, file, content, null);
    }

    public AiWorkerOcrResponse processFile(Long userId, FileEntity file, byte[] content, String pdfPassword) {
        if (content == null || content.length == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "File content is empty");
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("user_id", String.valueOf(userId));

        ByteArrayResource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return file.getObjectKey() != null ? file.getObjectKey() : "upload";
            }
        };

        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(parseMediaType(file.getContentType()));
        fileHeaders.setContentDispositionFormData("file", resource.getFilename());

        HttpEntity<ByteArrayResource> filePart = new HttpEntity<>(resource, fileHeaders);
        body.add("file", filePart);

        String broker = extractBrokerHint(file);
        if (broker != null) {
            body.add("broker", broker);
        }
        if (pdfPassword != null && !pdfPassword.isBlank()) {
            body.add("pdf_password", pdfPassword);
        }

        try {
            AiWorkerOcrResponse response = webClient.post()
                    .uri("/ocr")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(body))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, clientResponse -> clientResponse.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .map(bodyText -> mapAiWorkerError(clientResponse.statusCode(), bodyText)))
                    .bodyToMono(AiWorkerOcrResponse.class)
                    .block(requestTimeout());

            if (response == null) {
                throw new BusinessException(ErrorCode.OCR_PARSE_FAILED, "Empty OCR response");
            }
            return response;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            String message = isTimeoutException(ex)
                    ? "AI Worker OCR timeout"
                    : "AI Worker OCR failed: " + ex.getMessage();
            throw new BusinessException(ErrorCode.OCR_PARSE_FAILED, message);
        }
    }

    private BusinessException mapAiWorkerError(HttpStatusCode status, String bodyText) {
        String code = extractErrorCode(bodyText);
        String message = extractErrorMessage(bodyText);
        if ("PDF_PASSWORD_REQUIRED".equals(code) || containsCode(bodyText, "PDF_PASSWORD_REQUIRED")) {
            return new BusinessException(
                    ErrorCode.PDF_PASSWORD_REQUIRED,
                    defaultIfBlank(message, "此 PDF 需要密碼，請輸入密碼後繼續處理"));
        }
        if ("PDF_PASSWORD_INVALID".equals(code) || containsCode(bodyText, "PDF_PASSWORD_INVALID")) {
            return new BusinessException(
                    ErrorCode.PDF_PASSWORD_INVALID,
                    defaultIfBlank(message, "PDF 密碼錯誤，請重新輸入"));
        }

        String detail = defaultIfBlank(message, bodyText);
        String statusMessage = "AI Worker OCR failed: " + status.value();
        if (detail != null && !detail.isBlank()) {
            statusMessage += " " + detail;
        }
        return new BusinessException(ErrorCode.OCR_PARSE_FAILED, trimMessage(statusMessage));
    }

    private String extractErrorCode(String bodyText) {
        JsonNode detail = readDetail(bodyText);
        if (detail != null && detail.isObject()) {
            JsonNode code = detail.get("code");
            if (code != null && code.isTextual()) {
                return code.asText();
            }
        }
        JsonNode root = readJson(bodyText);
        if (root != null) {
            JsonNode code = root.get("code");
            if (code != null && code.isTextual()) {
                return code.asText();
            }
        }
        return null;
    }

    private String extractErrorMessage(String bodyText) {
        JsonNode detail = readDetail(bodyText);
        if (detail != null) {
            if (detail.isTextual()) {
                return detail.asText();
            }
            if (detail.isObject()) {
                JsonNode message = detail.get("message");
                if (message != null && message.isTextual()) {
                    return message.asText();
                }
            }
        }
        return null;
    }

    private JsonNode readDetail(String bodyText) {
        JsonNode root = readJson(bodyText);
        if (root == null) {
            return null;
        }
        return root.get("detail");
    }

    private JsonNode readJson(String bodyText) {
        if (bodyText == null || bodyText.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(bodyText);
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean containsCode(String bodyText, String code) {
        return bodyText != null && bodyText.contains(code);
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String trimMessage(String message) {
        if (message == null) {
            return null;
        }
        String trimmed = message.trim();
        return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
    }

    /**
     * Extract broker hint from file name or metadata.
     */
    private String extractBrokerHint(FileEntity file) {
        if (file == null || file.getObjectKey() == null) {
            return null;
        }
        String name = file.getObjectKey().toLowerCase();
        if (name.contains("fubon")) {
            return "fubon";
        }
        if (name.contains("yuanta")) {
            return "yuanta";
        }
        if (name.contains("cathay")) {
            return "cathay";
        }
        if (name.contains("sinopac")) {
            return "sinopac";
        }
        return null;
    }

    private MediaType parseMediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (Exception ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private Duration requestTimeout() {
        int timeoutSeconds = properties.getTimeoutSeconds() != null ? properties.getTimeoutSeconds() : 300;
        return Duration.ofSeconds(Math.max(1, timeoutSeconds));
    }

    private boolean isTimeoutException(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof TimeoutException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("timeout")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
