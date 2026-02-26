package tw.bk.apppersistence.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import tw.bk.appcommon.enums.AssetType;
import tw.bk.appcommon.enums.ConversationMessageStatus;
import tw.bk.appcommon.enums.ConversationRole;
import tw.bk.appcommon.enums.InstrumentStatus;
import tw.bk.appcommon.enums.OcrJobStatus;
import tw.bk.appcommon.enums.TradeSide;
import tw.bk.appcommon.enums.TradeSource;

class PersistenceEntityBehaviorTest {

    @Test
    void conversationMessageEntity_shouldResolveRoleAndStatusEnum() {
        ConversationMessageEntity entity = new ConversationMessageEntity();
        entity.setRole("assistant");
        entity.setStatus("completed");

        assertEquals(ConversationRole.ASSISTANT, entity.getRoleEnum());
        assertEquals(ConversationMessageStatus.COMPLETED, entity.getStatusEnum());

        entity.setRole("unknown");
        entity.setStatus("invalid");
        assertNull(entity.getRoleEnum());
        assertNull(entity.getStatusEnum());
    }

    @Test
    void instrumentEntity_shouldResolveAssetTypeAndStatusEnum() {
        InstrumentEntity entity = new InstrumentEntity();
        entity.setAssetType("etf");
        entity.setStatus("suspended");

        assertEquals(AssetType.ETF, entity.getAssetTypeEnum());
        assertEquals(InstrumentStatus.SUSPENDED, entity.getStatusEnum());

        entity.setAssetType("bad-type");
        entity.setStatus("bad-status");
        assertNull(entity.getAssetTypeEnum());
        assertNull(entity.getStatusEnum());
    }

    @Test
    void ocrJobEntity_shouldResolveStatusEnum() {
        OcrJobEntity entity = new OcrJobEntity();
        entity.setStatus("running");
        assertEquals(OcrJobStatus.RUNNING, entity.getStatusEnum());

        entity.setStatus("N/A");
        assertNull(entity.getStatusEnum());
    }

    @Test
    void stockTradeEntity_shouldResolveSideAndSourceEnum() {
        StockTradeEntity entity = new StockTradeEntity();
        entity.setSide("buy");
        entity.setSource("ocr");

        assertEquals(TradeSide.BUY, entity.getSideEnum());
        assertEquals(TradeSource.OCR, entity.getSourceEnum());

        entity.setSide("?");
        entity.setSource("?");
        assertNull(entity.getSideEnum());
        assertNull(entity.getSourceEnum());
    }

    @Test
    void statementTradeEntity_shouldResolveSideEnum() {
        StatementTradeEntity entity = new StatementTradeEntity();
        entity.setSide("sell");
        assertEquals(TradeSide.SELL, entity.getSideEnum());

        entity.setSide("?");
        assertNull(entity.getSideEnum());
    }
}
