package tw.bk.appfiles.model;

import java.util.Map;
import tw.bk.apppersistence.entity.FileEntity;

public record PresignResult(
                FileEntity file,
                String uploadUrl,
                String method,
                Map<String, String> headers) {
}
