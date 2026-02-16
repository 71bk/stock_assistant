package tw.bk.apprag.client;

import java.time.Duration;
import java.util.List;
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

@Component
@RequiredArgsConstructor
public class AiWorkerRagClient {
    private final WebClient webClient;

    public AiWorkerIngestResponse ingestText(Long userId, String text, String title, String sourceType,
            List<String> tags) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Unauthorized");
        }
        if (text == null || text.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Text is empty");
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("user_id", String.valueOf(userId));
        body.add("text", text);
        body.add("title", title);
        if (sourceType != null && !sourceType.isBlank()) {
            body.add("source_type", sourceType);
        }
        if (tags != null && !tags.isEmpty()) {
            body.add("tags", String.join(",", tags));
        }

        try {
            AiWorkerIngestResponse response = webClient
                    .post()
                    .uri("/ingest/text")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(body))
                    .retrieve()
                    .bodyToMono(AiWorkerIngestResponse.class)
                    .block();

            if (response == null) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Empty ingestion response");
            }
            return response;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI Worker ingestion failed: " + ex.getMessage());
        }
    }

    public AiWorkerIngestResponse ingestFile(Long userId, String filename, String contentType, byte[] content,
            String title, String sourceType, List<String> tags) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Unauthorized");
        }
        if (content == null || content.length == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "File content is empty");
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("user_id", String.valueOf(userId));
        if (title != null && !title.isBlank()) {
            body.add("title", title);
        }
        if (sourceType != null && !sourceType.isBlank()) {
            body.add("source_type", sourceType);
        }
        if (tags != null && !tags.isEmpty()) {
            body.add("tags", String.join(",", tags));
        }

        ByteArrayResource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename != null && !filename.isBlank() ? filename : "upload";
            }
        };

        HttpHeaders fileHeaders = new HttpHeaders();
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (contentType != null && !contentType.isBlank()) {
            mediaType = MediaType.parseMediaType(contentType);
        }
        fileHeaders.setContentType(mediaType);
        fileHeaders.setContentDispositionFormData("file", resource.getFilename());

        HttpEntity<ByteArrayResource> filePart = new HttpEntity<>(resource, fileHeaders);
        body.add("file", filePart);

        try {
            AiWorkerIngestResponse response = webClient
                    .post()
                    .uri("/ingest")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(body))
                    .retrieve()
                    .bodyToMono(AiWorkerIngestResponse.class)
                    .block();

            if (response == null) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Empty ingestion response");
            }
            return response;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI Worker ingestion failed: " + ex.getMessage());
        }
    }

    public AiWorkerIngestResponse ingestUrl(
            Long userId,
            String fileUrl,
            String filename,
            String contentType,
            String title,
            String sourceType,
            List<String> tags) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Unauthorized");
        }
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "File URL is required");
        }

        var payload = new java.util.LinkedHashMap<String, Object>();
        payload.put("user_id", userId);
        payload.put("file_url", fileUrl);
        if (filename != null && !filename.isBlank()) {
            payload.put("filename", filename);
        }
        if (contentType != null && !contentType.isBlank()) {
            payload.put("content_type", contentType);
        }
        if (title != null && !title.isBlank()) {
            payload.put("title", title);
        }
        if (sourceType != null && !sourceType.isBlank()) {
            payload.put("source_type", sourceType);
        }
        if (tags != null && !tags.isEmpty()) {
            payload.put("tags", tags);
        }

        try {
            AiWorkerIngestResponse response = webClient
                    .post()
                    .uri("/ingest/url")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(AiWorkerIngestResponse.class)
                    .block();

            if (response == null) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Empty ingestion response");
            }
            return response;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI Worker ingestion failed: " + ex.getMessage());
        }
    }

    public AiWorkerQueryResponse query(Long userId, String query, Integer topK, String sourceType) {
        return query(userId, query, topK, sourceType, null);
    }

    public AiWorkerQueryResponse query(Long userId, String query, Integer topK, String sourceType, Duration timeout) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Unauthorized");
        }
        if (query == null || query.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Query is empty");
        }

        var payload = new java.util.LinkedHashMap<String, Object>();
        payload.put("user_id", userId);
        payload.put("query", query);
        if (topK != null) {
            payload.put("top_k", topK);
        }
        if (sourceType != null && !sourceType.isBlank()) {
            payload.put("source_type", sourceType);
        }

        try {
            var responseMono = webClient
                    .post()
                    .uri("/query")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(AiWorkerQueryResponse.class);
            AiWorkerQueryResponse response = timeout != null
                    ? responseMono.block(timeout)
                    : responseMono.block();

            if (response == null) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Empty query response");
            }
            return response;
        } catch (Exception ex) {
            if (isTimeoutException(ex)) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI Worker query timeout");
            }
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI Worker query failed: " + ex.getMessage());
        }
    }

    private boolean isTimeoutException(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof java.util.concurrent.TimeoutException) {
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
