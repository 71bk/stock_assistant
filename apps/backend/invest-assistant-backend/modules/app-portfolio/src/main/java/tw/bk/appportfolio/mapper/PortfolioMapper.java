package tw.bk.appportfolio.mapper;

import org.springframework.stereotype.Component;
import tw.bk.appportfolio.model.PortfolioRefView;
import tw.bk.appportfolio.model.PortfolioValuationView;
import tw.bk.appportfolio.model.PortfolioView;
import tw.bk.appportfolio.model.TradeView;
import tw.bk.apppersistence.entity.InstrumentEntity;
import tw.bk.apppersistence.entity.PortfolioEntity;
import tw.bk.apppersistence.entity.PortfolioValuationEntity;
import tw.bk.apppersistence.entity.StockTradeEntity;

/**
 * 將 portfolio 相關 entity 轉成對外 view model 的純對應工具。
 *
 * <p>無狀態、無外部依賴，由 {@code PortfolioService} 直接持有，
 * 把 view 組裝邏輯從 service 抽出以縮小其職責。
 */
@Component
public class PortfolioMapper {

    public PortfolioView toPortfolioView(PortfolioEntity entity) {
        return new PortfolioView(
                entity.getId(),
                entity.getName(),
                entity.getBaseCurrency());
    }

    public PortfolioRefView toPortfolioRefView(PortfolioEntity entity) {
        return new PortfolioRefView(
                entity.getId(),
                entity.getUserId());
    }

    public PortfolioValuationView toPortfolioValuationView(PortfolioValuationEntity entity) {
        return new PortfolioValuationView(
                entity.getAsOfDate(),
                entity.getTotalValue(),
                entity.getCashValue(),
                entity.getPositionsValue(),
                entity.getBaseCurrency());
    }

    public TradeView toTradeView(StockTradeEntity entity, InstrumentEntity instrument) {
        return new TradeView(
                entity.getId(),
                entity.getInstrumentId(),
                instrument != null ? instrument.getSymbolKey() : null,
                instrument != null ? instrument.getTicker() : null,
                instrument != null ? instrument.getNameZh() : null,
                instrument != null ? instrument.getNameEn() : null,
                entity.getTradeDate(),
                entity.getSettlementDate(),
                entity.getSideEnum(),
                entity.getQuantity(),
                entity.getPrice(),
                entity.getCurrency(),
                entity.getGrossAmount(),
                entity.getFee(),
                entity.getTax(),
                entity.getNetAmount(),
                entity.getSourceEnum(),
                entity.getAccountId());
    }
}
