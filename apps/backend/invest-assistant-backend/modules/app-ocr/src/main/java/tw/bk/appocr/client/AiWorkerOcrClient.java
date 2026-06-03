package tw.bk.appocr.client;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
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

    public AiWorkerOcrClient(
            AiWorkerProperties properties,
            @Qualifier("aiWorkerOcrWebClient") WebClient webClient) {
        this.properties = properties;
        this.webClient = webClient;
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

        try {
            AiWorkerOcrResponse response = webClient.post()
                    .uri("/ocr")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(body))
                    .retrieve()
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
        int timeoutSeconds = properties.getTimeoutSeconds() != null ? properties.getTimeoutSeconds() : 120;
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