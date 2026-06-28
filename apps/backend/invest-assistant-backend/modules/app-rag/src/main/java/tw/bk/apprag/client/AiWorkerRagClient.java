package tw.bk.apprag.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
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
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;

@Component
public class AiWorkerRagClient {
    private static final ObjectMapper ERROR_MAPPER = new ObjectMapper();

    private final WebClient webClient;
    private final AiWorkerRagProperties properties;

    public AiWorkerRagClient(
            @Qualifier("aiWorkerRagWebClient") WebClient webClient,
            AiWorkerRagProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

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
            return blockRequired(webClient
                    .post()
                    .uri("/ingest/text")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(body))
                    .retrieve()
                    .bodyToMono(AiWorkerIngestResponse.class),
                    "Empty ingestion response");
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ingestionException(ex);
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
        fileHeaders.setContentType(parseMediaType(contentType));
        fileHeaders.setContentDispositionFormData("file", resource.getFilename());

        HttpEntity<ByteArrayResource> filePart = new HttpEntity<>(resource, fileHeaders);
        body.add("file", filePart);

        try {
            return blockRequired(webClient
                    .post()
                    .uri("/ingest")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(body))
                    .retrieve()
                    .bodyToMono(AiWorkerIngestResponse.class),
                    "Empty ingestion response");
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ingestionException(ex);
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
            return blockRequired(webClient
                    .post()
                    .uri("/ingest/url")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(AiWorkerIngestResponse.class),
                    "Empty ingestion response");
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ingestionException(ex);
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
            Duration effectiveTimeout = timeout != null ? timeout : requestTimeout();
            AiWorkerQueryResponse response = webClient
                    .post()
                    .uri("/query")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(AiWorkerQueryResponse.class)
                    .block(effectiveTimeout);

            if (response == null) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Empty query response");
            }
            return response;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            if (isTimeoutException(ex)) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI Worker query timeout");
            }
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI Worker query failed: " + ex.getMessage());
        }
    }

    public void deleteDocument(Long userId, Long documentId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Unauthorized");
        }
        if (documentId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Document ID is required");
        }

        try {
            webClient
                    .delete()
                    .uri(uriBuilder -> uriBuilder
                            .path("/ingest/documents/{id}")
                            .queryParam("user_id", userId)
                            .build(documentId))
                    .retrieve()
                    .toBodilessEntity()
                    .block(requestTimeout());
        } catch (WebClientResponseException.NotFound ex) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Document not found");
        } catch (WebClientResponseException.Forbidden ex) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN, "Access denied");
        } catch (WebClientResponseException.BadRequest ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid delete request");
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            if (isTimeoutException(ex)) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI Worker delete timeout");
            }
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI Worker delete failed: " + ex.getMessage());
        }
    }

    private <T> T blockRequired(Mono<T> mono, String emptyMessage) {
        T response = mono.block(requestTimeout());
        if (response == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, emptyMessage);
        }
        return response;
    }

    private BusinessException ingestionException(Exception ex) {
        // The ai-worker returns 4xx for client-side problems (unparseable / empty /
        // unsupported file, or a scanned PDF needing OCR). Surface those as a 4xx
        // with the worker's reason so they read as bad input — not a server error
        // that pages Sentry.
        if (ex instanceof WebClientResponseException wcre && wcre.getStatusCode().is4xxClientError()) {
            return new BusinessException(ErrorCode.VALIDATION_ERROR, extractWorkerDetail(wcre));
        }
        if (isTimeoutException(ex)) {
            return new BusinessException(ErrorCode.INTERNAL_ERROR, "AI Worker ingestion timeout");
        }
        return new BusinessException(ErrorCode.INTERNAL_ERROR, "AI Worker ingestion failed: " + ex.getMessage());
    }

    private String extractWorkerDetail(WebClientResponseException ex) {
        try {
            String body = ex.getResponseBodyAsString();
            if (body != null && !body.isBlank()) {
                JsonNode detail = ERROR_MAPPER.readTree(body).get("detail");
                if (detail != null && detail.isTextual() && !detail.asText().isBlank()) {
                    return detail.asText();
                }
            }
        } catch (Exception ignored) {
            // fall through to a generic message
        }
        return "文件無法解析，請確認檔案內容或格式是否支援";
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