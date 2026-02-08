-- Ensure settlement_date is not earlier than trade_date (enforced for new/updated rows)
ALTER TABLE app.stock_trades
    ADD CONSTRAINT chk_stock_trades_settlement_not_before_trade
    CHECK (settlement_date IS NULL OR settlement_date >= trade_date)
    NOT VALID;

-- After cleaning existing data, run:
-- ALTER TABLE app.stock_trades VALIDATE CONSTRAINT chk_stock_trades_settlement_not_before_trade;
