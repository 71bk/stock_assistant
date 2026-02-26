package tw.bk.appocr.service;

import java.util.Locale;
import org.springframework.stereotype.Service;
import tw.bk.apppersistence.entity.FileEntity;

@Service
public class OcrDedupeContentKeyResolver {

    public String resolve(FileEntity file) {
        if (file == null) {
            return null;
        }

        String sha256 = file.getSha256();
        if (sha256 != null) {
            String normalizedSha = sha256.trim().toLowerCase(Locale.ROOT);
            if (!normalizedSha.isBlank()) {
                return "sha:" + normalizedSha;
            }
        }

        Long fileId = file.getId();
        if (fileId != null) {
            return "file-id:" + fileId;
        }

        return null;
    }
}
