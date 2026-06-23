package tw.bk.appocr.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appportfolio.model.TradeCommand;
import tw.bk.appportfolio.service.PortfolioService;
import tw.bk.apppersistence.entity.StatementTradeEntity;
import tw.bk.apppersistence.repository.StatementTradeRepository;

/**
 * 在獨立交易中匯入單一 OCR 草稿。
 *
 * <p>Confirm 是批次操作。若把單筆 {@code createTrade} 放在 confirm 的外層交易裡，任何一筆
 * 丟出 {@link BusinessException}（幣別不符、查無商品、撞到去重唯一索引等）都會把共用交易
 * 標記為 rollback-only，導致整批已成功的匯入在 commit 時一起回滾並回 500，違背
 * confirm「部分成功 + 逐筆錯誤回報」的設計。
 *
 * <p>因此把單筆匯入抽到獨立 bean，並以 {@link Propagation#REQUIRES_NEW} 各自開新交易：
 * 單筆失敗只回滾該筆，外層 confirm 的迴圈才能真正接住錯誤並繼續匯入其他筆。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OcrImportTxService {

    private final PortfolioService portfolioService;
    private final StatementTradeRepository statementTradeRepository;
    private final OcrTradeCommandFactory tradeCommandFactory;

    /**
     * 在獨立交易中將單一草稿轉成交易並刪除草稿；失敗時只回滾這一筆，不影響整批。
     *
     * @param userId      使用者 ID
     * @param portfolioId 投資組合 ID
     * @param draft       草稿實體
     * @throws BusinessException 當草稿無法轉成有效交易，或建立交易時違反業務規則
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void importDraft(Long userId, Long portfolioId, StatementTradeEntity draft) {
        TradeCommand command = tradeCommandFactory.toTradeCommand(draft);
        if (command == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "買賣方向無效 (side)");
        }
        portfolioService.createTrade(userId, portfolioId, command);
        statementTradeRepository.deleteById(draft.getId());
    }
}
