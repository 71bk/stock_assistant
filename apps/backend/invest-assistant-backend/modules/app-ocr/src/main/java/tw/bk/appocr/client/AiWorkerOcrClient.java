package tw.bk.appocr.client;

import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class AiWorkerOcrClient {

    private final AiWorkerProperties properties;

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

        String baseUrl = properties.getBaseUrl();

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("user_id", String.valueOf(userId));

        ByteArrayResource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return file.getObjectKey() != null ? file.getObjectKey() : "upload";
            }
        };

        HttpHeaders fileHeaders = new HttpHeaders();
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (file.getContentType() != null && !file.getContentType().isBlank()) {
            mediaType = MediaType.parseMediaType(file.getContentType());
        }
        fileHeaders.setContentType(mediaType);
        fileHeaders.setContentDispositionFormData("file", resource.getFilename());

        HttpEntity<ByteArrayResource> filePart = new HttpEntity<>(resource, fileHeaders);
        body.add("file", filePart);

        String broker = extractBrokerHint(file);
        if (broker != null) {
            body.add("broker", broker);
        }

        try {
            WebClient client = WebClient.builder()
                    .baseUrl(baseUrl)
                    .build();

            AiWorkerOcrResponse response = client.post()
                    .uri("/ocr")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(body))
                    .retrieve()
                    .bodyToMono(AiWorkerOcrResponse.class)
                    .block();

            if (response == null) {
                throw new BusinessException(ErrorCode.OCR_PARSE_FAILED, "Empty OCR response");
            }
            return response;
        } catch (Exception ex) {
            throw new BusinessException(
                    ErrorCode.OCR_PARSE_FAILED,
                    "AI Worker OCR failed: " + ex.getMessage());
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
}
