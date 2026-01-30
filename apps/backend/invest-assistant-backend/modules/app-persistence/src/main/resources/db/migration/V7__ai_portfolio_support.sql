-- 擴展 ai_reports 支援投資組合分析
-- 現有表只有 instrument_id，增加 portfolio_id 和 report_type 以支援不同類型的 AI 分析

ALTER TABLE app.ai_reports 
ADD COLUMN portfolio_id BIGINT REFERENCES app.portfolios(id) ON DELETE SET NULL;

ALTER TABLE app.ai_reports 
ADD COLUMN report_type TEXT NOT NULL DEFAULT 'INSTRUMENT' 
    CHECK (report_type IN ('INSTRUMENT', 'PORTFOLIO', 'GENERAL'));

-- 建立索引加速查詢
CREATE INDEX IF NOT EXISTS idx_ai_reports_portfolio ON app.ai_reports(user_id, portfolio_id);
CREATE INDEX IF NOT EXISTS idx_ai_reports_type ON app.ai_reports(user_id, report_type);

-- 更新欄位註解
COMMENT ON COLUMN app.ai_reports.portfolio_id IS '投資組合 ID（PORTFOLIO 類型時使用）';
COMMENT ON COLUMN app.ai_reports.report_type IS '報告類型：INSTRUMENT=單一商品分析, PORTFOLIO=投資組合分析, GENERAL=一般問答';
