package tw.bk.appocr.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Service;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.apppersistence.entity.StatementTradeEntity;

@Service
public class OcrRowHashService {

    public String buildRowHash(Long statementId, StatementTradeEntity trade) {
        String payload = statementId + "|"
                + safe(trade.getInstrumentId()) + "|"
                + safe(trade.getRawTicker()) + "|"
                + safe(trade.getTradeDate()) + "|"
                + safe(trade.getSide()) + "|"
                + safe(trade.getQuantity()) + "|"
                + safe(trade.getPrice()) + "|"
                + safe(trade.getCurrency()) + "|"
                + safe(trade.getFee()) + "|"
                + safe(trade.getTax());
        return sha256Hex(payload);
    }

    private String safe(Object value) {
        return value == null ? "" : value.toString();
    }

    private String sha256Hex(String input) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "SHA-256 not available");
        }
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
