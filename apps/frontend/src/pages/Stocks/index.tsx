/**
 * 股票行情
 * 搜尋股票、查看行情、K線圖表、AI 分析
 */

import React, { useEffect } from 'react';
import { Typography, Card, Row, Col, Statistic, Tag, Empty } from 'antd';
import { ArrowUpOutlined, ArrowDownOutlined } from '@ant-design/icons';
import { InstrumentSearch } from '../../components/common/InstrumentSearch';
import { PriceChart } from '../../components/charts/PriceChart';
import { useStocksStore } from '../../stores/stocks.store';

const { Title, Text } = Typography;

const Stocks: React.FC = () => {
  const { selectedInstrument, quotes, setSelectedInstrument, fetchQuote } = useStocksStore();

  useEffect(() => {
    const symbolKey = selectedInstrument?.symbolKey || (selectedInstrument as any)?.symbol_key;
    if (symbolKey) {
      fetchQuote(symbolKey);
    }
  }, [selectedInstrument, fetchQuote]);

  const symbolKey = selectedInstrument?.symbolKey || (selectedInstrument as any)?.symbol_key;
  const quote = symbolKey ? quotes[symbolKey] : null;
  const isUp = quote ? parseFloat(quote.change) >= 0 : false;

  return (
    <div>
      <Title level={2}>股票行情</Title>
      
      <Card style={{ marginBottom: 24 }}>
        <div style={{ maxWidth: 600, margin: '0 auto' }}>
          <Text style={{ display: 'block', marginBottom: 8 }}>搜尋代號或名稱：</Text>
          <InstrumentSearch onSelect={setSelectedInstrument} />
        </div>
      </Card>

      {selectedInstrument ? (
        <>
          <Card style={{ marginBottom: 24 }}>
            <div style={{ display: 'flex', alignItems: 'center', marginBottom: 16 }}>
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

            <Row gutter={24}>
              <Col span={6}>
                <Statistic
                  title="現價"
                  value={quote?.price || '-'}
                  precision={2}
                  prefix={selectedInstrument.currency === 'USD' ? '$' : 'NT$'}
                  valueStyle={{ color: isUp ? '#3f8600' : '#cf1322' }}
                />
              </Col>
              <Col span={6}>
                <Statistic
                  title="漲跌"
                  value={quote?.change || '-'}
                  precision={2}
                  valueStyle={{ color: isUp ? '#3f8600' : '#cf1322' }}
                  prefix={isUp ? <ArrowUpOutlined /> : <ArrowDownOutlined />}
                />
              </Col>
              <Col span={6}>
                <Statistic
                  title="漲跌幅"
                  value={quote?.changePercent || '-'} // changePercent in CamelCase
                  precision={2}
                  valueStyle={{ color: isUp ? '#3f8600' : '#cf1322' }}
                  suffix="%"
                />
              </Col>
              {/* Volume if available */}
            </Row>
          </Card>

          <Card title="股價走勢 (K線圖)">
             <PriceChart symbolKey={symbolKey} height={500} />
          </Card>
        </>
      ) : (
        <Card>
          <Empty description="請先搜尋股票以查看行情" />
        </Card>
      )}
    </div>
  );
};

export default Stocks;
