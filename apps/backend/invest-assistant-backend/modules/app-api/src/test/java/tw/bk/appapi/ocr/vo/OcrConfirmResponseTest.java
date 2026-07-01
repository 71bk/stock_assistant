package tw.bk.appapi.ocr.vo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import tw.bk.appocr.model.ConfirmResult;
import tw.bk.appocr.model.OcrDraftError;

class OcrConfirmResponseTest {

    @Test
    void from_shouldReuseDraftErrorShapeAndKeepApiIdAsString() {
        ConfirmResult result = ConfirmResult.builder()
                .importedCount(2)
                .errors(List.of(new OcrDraftError<>(42L, "duplicate")))
                .build();

        OcrConfirmResponse response = OcrConfirmResponse.from(result);

        assertEquals(2, response.getImportedCount());
        assertEquals("42", response.getErrors().getFirst().draftId());
        assertEquals("duplicate", response.getErrors().getFirst().reason());
    }
}
