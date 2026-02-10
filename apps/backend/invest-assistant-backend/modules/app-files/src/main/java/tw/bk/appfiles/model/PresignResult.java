package tw.bk.appfiles.model;

import java.util.Map;

public record PresignResult(
                Long fileId,
                String objectKey,
                String uploadUrl,
                String method,
                Map<String, String> headers,
                boolean alreadyExists) {
}
