package tw.bk.appocr.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.bk.apppersistence.entity.FileEntity;

class OcrDedupeContentKeyResolverTest {

    private OcrDedupeContentKeyResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new OcrDedupeContentKeyResolver();
    }

    @Test
    void resolve_shouldUseNormalizedShaWhenPresent() {
        FileEntity file = new FileEntity();
        file.setSha256(" AbCdEf ");
        file.setId(11L);

        String key = resolver.resolve(file);

        assertEquals("sha:abcdef", key);
    }

    @Test
    void resolve_shouldFallbackToFileIdWhenShaIsBlank() {
        FileEntity file = new FileEntity();
        file.setSha256("   ");
        file.setId(11L);

        String key = resolver.resolve(file);

        assertEquals("file-id:11", key);
    }

    @Test
    void resolve_shouldReturnNullWhenNoShaAndNoId() {
        FileEntity file = new FileEntity();
        file.setSha256(null);
        file.setId(null);

        String key = resolver.resolve(file);

        assertNull(key);
    }
}
