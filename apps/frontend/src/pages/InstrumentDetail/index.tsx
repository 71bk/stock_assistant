import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Typography, Card, Row, Col, Statistic, Tag, Button, Skeleton, Result } from 'antd';
import { ArrowUpOutlined, ArrowDownOutlined, RobotOutlined, ArrowLeftOutlined } from '@ant-design/icons';
import { PriceChart } from '../../components/charts/PriceChart';
import { PageContainer } from '../../components/layout/PageContainer';
import { stocksApi, type Instrument, type EtfProfile, type WarrantProfile } from '../../api/stocks.api';
import { useStocksStore } from '../../stores/stocks.store';
import { useAiStore } from '../../stores/ai.store';
import { AiAnalysisModal } from '../../components/ai/AiAnalysisModal';
import { logger } from '../../utils/logger';

const { Title, Text } = Typography;

const ProfileRow: React.FC<{ label: string; value?: string | null }> = ({ label, value }) => (
  <Col xs={24} sm={12}>
    <Text type="secondary">{label}：</Text>
    <Text>{value || '-'}</Text>
  </Col>
);

const InstrumentDetail: React.FC = () => {
  const { symbolKey = '' } = useParams();
  const navigate = useNavigate();
  const { quotes, fetchQuote } = useStocksStore();
  const { startAnalysis, resetAnalysis } = useAiStore();

  const [instrument, setInstrument] = useState<Instrument | null>(null);
  const [etfProfile, setEtfProfile] = useState<EtfProfile | null>(null);
  const [warrantProfile, setWarrantProfile] = useState<WarrantProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [isAiModalOpen, setIsAiModalOpen] = useState(false);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setNotFound(false);
    stocksApi.getInstrumentDetail(symbolKey)
      .then((res) => {
        if (!active) return;
        setInstrument(res.instrument);
        setEtfProfile(res.etfProfile);
        setWarrantProfile(res.warrantProfile);
        fetchQuote(symbolKey);
      })
      .catch(() => {
        if (!active) return;
        // Missing / unknown symbolKey is an expected user-facing case, not a Sentry error.
        logger.info('Instrument detail not found', { symbolKey });
        setNotFound(true);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => { active = false; };
  }, [symbolKey, fetchQuote]);

  const quote = quotes[symbolKey] || null;
  const changeVal = quote ? parseFloat(quote.change) : 0;
  const isUp = changeVal >= 0;
  const pnlColor = isUp ? '#3f8600' : '#cf1322';

  const handleStartAiAnalysis = async () => {
    if (!instrument?.instrumentId) return;
    setIsAiModalOpen(true);
    await startAnalysis({
      instrumentId: instrument.instrumentId,
      reportType: 'INSTRUMENT',
      prompt: `請分析 ${instrument.ticker} (${instrument.nameZh || instrument.nameEn}) 最近的市場表現、技術面趨勢與潛在風險。`,
    });
  };

  const handleCloseAiModal = () => {
    setIsAiModalOpen(false);
    resetAnalysis();
  };

  if (loading) {
    return (
      <PageContainer>
        <Card><Skeleton active paragraph={{ rows: 6 }} /></Card>
      </PageContainer>
    );
  }

  if (notFound || !instrument) {
    return (
      <PageContainer>
        <Result
          status="404"
          title="找不到商品"
          subTitle={`查無此商品：${symbolKey}`}
          extra={<Button type="primary" onClick={() => navigate(-1)}>返回</Button>}
        />
      </PageContainer>
    );
  }

  return (
    <PageContainer>
      <Button type="text" icon={<ArrowLeftOutlined />} onClick={() => navigate(-1)} style={{ marginBottom: 8 }}>
        返回
      </Button>

      <Card style={{ marginBottom: 24 }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 12, marginBottom: 16 }}>
          <div style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
            <Title level={3} style={{ margin: 0 }}>{instrument.ticker}</Title>
            <Text type="secondary" style={{ fontSize: 18 }}>{instrument.nameZh || instrument.nameEn}</Text>
            <Tag color={instrument.market === 'US' ? 'blue' : 'green'}>{instrument.exchange}</Tag>
            <Tag>{instrument.assetType}</Tag>
          </div>
          <Button icon={<RobotOutlined />} onClick={handleStartAiAnalysis}>AI 行情分析</Button>
        </div>

        <Row gutter={24}>
          <Col xs={8}>
            <Statistic
              title="現價"
              value={quote?.price ?? '-'}
              precision={2}
              prefix={instrument.currency === 'USD' ? '$' : 'NT$'}
              styles={{ content: { color: pnlColor } }}
            />
          </Col>
          <Col xs={8}>
            <Statistic
              title="漲跌"
              value={quote?.change ?? '-'}
              precision={2}
              styles={{ content: { color: pnlColor } }}
              prefix={isUp ? <ArrowUpOutlined /> : <ArrowDownOutlined />}
            />
          </Col>
          <Col xs={8}>
            <Statistic
              title="漲跌幅"
              value={quote?.changePercent ?? quote?.changePct ?? '-'}
              precision={2}
              styles={{ content: { color: pnlColor } }}
              suffix="%"
            />
          </Col>
        </Row>
      </Card>

      {etfProfile && (
        <Card title="ETF 資訊" size="small" style={{ marginBottom: 24 }}>
          <Row gutter={[16, 8]}>
            <ProfileRow label="追蹤類型" value={etfProfile.underlyingType} />
            <ProfileRow label="追蹤標的" value={etfProfile.underlyingName} />
            <ProfileRow label="資料日期" value={etfProfile.asOfDate} />
          </Row>
        </Card>
      )}

      {warrantProfile && (
        <Card title="權證資訊" size="small" style={{ marginBottom: 24 }}>
          <Row gutter={[16, 8]}>
            <ProfileRow label="標的代號" value={warrantProfile.underlyingSymbol} />
            <ProfileRow label="到期日" value={warrantProfile.expiryDate} />
          </Row>
        </Card>
      )}

      <Card title="股價走勢 (K線圖)">
        <PriceChart symbolKey={symbolKey} assetType={instrument.assetType} height={500} />
      </Card>

      <AiAnalysisModal
        open={isAiModalOpen}
        onClose={handleCloseAiModal}
        title={`${instrument.ticker} AI 行情解析`}
      />
    </PageContainer>
  );
};

export default InstrumentDetail;
