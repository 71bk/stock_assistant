/**
 * 股票行情
 * 搜尋股票、查看行情、K線圖表、AI 分析
 */

import React, { useEffect } from 'react';
import { Typography, Card, Row, Col, Statistic, Tag, Empty, Button } from 'antd';
import { ArrowUpOutlined, ArrowDownOutlined, RobotOutlined, PlusOutlined } from '@ant-design/icons';
import { InstrumentSearch } from '../../components/common/InstrumentSearch';
import { PriceChart } from '../../components/charts/PriceChart';
import { useStocksStore } from '../../stores/stocks.store';
import { useAiStore } from '../../stores/ai.store';
import { AiAnalysisModal } from '../../components/ai/AiAnalysisModal';
import { AddInstrumentModal } from '../../components/stocks/AddInstrumentModal';

const { Title, Text } = Typography;

const Stocks: React.FC = () => {
  const { selectedInstrument, quotes, setSelectedInstrument, fetchQuote } = useStocksStore();
  const { startAnalysis, resetAnalysis } = useAiStore();
  const [isAiModalOpen, setIsAiModalOpen] = React.useState(false);
  const [isAddModalOpen, setIsAddModalOpen] = React.useState(false);

  useEffect(() => {
    if (selectedInstrument?.symbolKey) {
      fetchQuote(selectedInstrument.symbolKey);
    }
  }, [selectedInstrument, fetchQuote]);

  const symbolKey = selectedInstrument?.symbolKey;
  const quote = symbolKey ? quotes[symbolKey] : null;
  const changeVal = quote ? parseFloat(quote.change) : 0;
  const isUp = changeVal >= 0;

  const handleStartAiAnalysis = async () => {
    if (!selectedInstrument?.instrumentId) return;
    setIsAiModalOpen(true);
    await startAnalysis({
      instrumentId: selectedInstrument.instrumentId,
      reportType: 'INSTRUMENT',
      prompt: `請分析 ${selectedInstrument.ticker} (${selectedInstrument.nameZh}) 最近的市場表現、技術面趨勢與潛在風險。`
    });
  };

  const handleCloseAiModal = () => {
    setIsAiModalOpen(false);
    resetAnalysis();
  };

  return (
    <div>
      <Title level={2}>股票行情</Title>

      <Card style={{ marginBottom: 24 }}>
        <div style={{ maxWidth: 600, margin: '0 auto' }}>
          <Text style={{ display: 'block', marginBottom: 8 }}>搜尋代號或名稱：</Text>
          <div style={{ display: 'flex', gap: 8 }}>
            <InstrumentSearch onSelect={setSelectedInstrument} />
            <Button
              icon={<PlusOutlined />}
              onClick={() => setIsAddModalOpen(true)}
              title="新增找不到的標的"
            />
          </div>
        </div>
      </Card>

      {selectedInstrument ? (
        <>
          <Card style={{ marginBottom: 24 }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16 }}>
              <div style={{ display: 'flex', alignItems: 'center' }}>
                <Title level={3} style={{ margin: 0, marginRight: 16 }}>
                  {selectedInstrument.ticker}
                </Title>
                <Text type="secondary" style={{ fontSize: 18 }}>
                  {selectedInstrument.nameZh || selectedInstrument.nameEn}
                </Text>
                <Tag color={selectedInstrument.market === 'US' ? 'blue' : 'green'} style={{ marginLeft: 16 }}>
                  {selectedInstrument.exchange}
                </Tag>
              </div>
              <Button
                icon={<RobotOutlined />}
                onClick={handleStartAiAnalysis}
              >
                AI 行情分析
              </Button>
            </div>

            <Row gutter={24}>
              <Col span={6}>
                <Statistic
                  title="現價"
                  value={quote?.price || '-'}
                  precision={2}
                  prefix={selectedInstrument.currency === 'USD' ? '$' : 'NT$'}
                  styles={{ content: { color: isUp ? '#3f8600' : '#cf1322' } }}
                />
              </Col>
              <Col span={6}>
                <Statistic
                  title="漲跌"
                  value={quote?.change || '-'}
                  precision={2}
                  styles={{ content: { color: isUp ? '#3f8600' : '#cf1322' } }}
                  prefix={isUp ? <ArrowUpOutlined /> : <ArrowDownOutlined />}
                />
              </Col>
              <Col span={6}>
                <Statistic
                  title="漲跌幅"
                  value={quote?.changePercent || quote?.changePct || '-'}
                  precision={2}
                  styles={{ content: { color: isUp ? '#3f8600' : '#cf1322' } }}
                  suffix="%"
                />
              </Col>
              {/* Volume if available */}
            </Row>
          </Card>

          <Card title="股價走勢 (K線圖)">
             <PriceChart symbolKey={symbolKey!} height={500} />
          </Card>
        </>
      ) : (
        <Card>
          <Empty description="請先搜尋股票以查看行情" />
        </Card>
      )}

      <AiAnalysisModal
        open={isAiModalOpen}
        onClose={handleCloseAiModal}
        title={`${selectedInstrument?.ticker} AI 行情解析`}
      />

      <AddInstrumentModal
        open={isAddModalOpen}
        onCancel={() => setIsAddModalOpen(false)}
        onSuccess={(instrument) => {
          setSelectedInstrument(instrument);
          setIsAddModalOpen(false);
        }}
      />
    </div>
  );
};

export default Stocks;
