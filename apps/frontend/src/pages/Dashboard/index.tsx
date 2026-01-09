import React, { useEffect } from 'react';
import { Row, Col, Card, Statistic, Table, Typography, Tag, Skeleton } from 'antd';
import { ArrowUpOutlined, ArrowDownOutlined } from '@ant-design/icons';
import { useAuthStore } from '../../stores/auth.store';
import { usePortfolioStore } from '../../stores/portfolio.store';
import { formatCurrency } from '../../utils/format';

const { Title } = Typography;

// MOCK_ACTIVITY can be moved to store or kept here if it's purely UI mock until API is ready
const MOCK_ACTIVITY = [
  { id: '1', date: '2026-01-08', type: 'BUY', symbol: 'AAPL', qty: 10, price: 185.50, amount: 1855.00 },
  { id: '2', date: '2026-01-07', type: 'SELL', symbol: 'TSLA', qty: 5, price: 240.00, amount: 1200.00 },
  { id: '3', date: '2026-01-05', type: 'BUY', symbol: '2330', qty: 1000, price: 580, amount: 580000 },
];

const Dashboard: React.FC = () => {
  const { user } = useAuthStore();
  const { summary, isLoading, fetchPortfolioSummary } = usePortfolioStore();
  const baseCurrency = user?.preferences?.baseCurrency || 'TWD';

  useEffect(() => {
    fetchPortfolioSummary();
  }, [fetchPortfolioSummary]);

  if (isLoading && !summary) {
    return (
      <div style={{ padding: 24 }}>
        <Skeleton active paragraph={{ rows: 4 }} />
        <div style={{ marginTop: 40 }}>
          <Skeleton active paragraph={{ rows: 6 }} />
        </div>
      </div>
    );
  }

  // Fallback values if summary is null
  const totalAssets = summary?.totalMarketValue || 0;
  const totalPnl = summary?.totalPnl || 0;
  const roi = summary?.totalPnlPercent || 0;

  return (
    <div>
      <div style={{ marginBottom: 24 }}>
        <Title level={2}>Dashboard</Title>
      </div>

      {/* KPI Cards */}
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={8}>
          <Card>
            <Statistic
              title="Total Assets"
              value={totalAssets}
              precision={0}
              prefix={baseCurrency === 'USD' ? '$' : 'NT$'}
              formatter={(val) => formatCurrency(Number(val), baseCurrency)}
            />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card>
            <Statistic
              title="Total P/L"
              value={totalPnl}
              precision={0}
              styles={{ value: { color: totalPnl >= 0 ? '#3f8600' : '#cf1322' } }}
              prefix={totalPnl >= 0 ? <ArrowUpOutlined /> : <ArrowDownOutlined />}
              formatter={(val) => formatCurrency(Number(val), baseCurrency)}
            />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card>
            <Statistic
              title="Total ROI"
              value={roi}
              precision={2}
              styles={{ value: { color: roi >= 0 ? '#3f8600' : '#cf1322' } }}
              suffix="%"
            />
          </Card>
        </Col>
      </Row>

      {/* Chart Placeholder */}
      <Row gutter={[16, 16]} style={{ marginTop: 24 }}>
        <Col span={24}>
          <Card title="Asset Trend (30 Days)">
            <div style={{ height: 300, background: '#f5f5f5', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#999' }}>
              Chart Component Placeholder
            </div>
          </Card>
        </Col>
      </Row>

      {/* Recent Activity */}
      <Row gutter={[16, 16]} style={{ marginTop: 24 }}>
        <Col span={24}>
          <Card title="Recent Activity">
            <Table
              dataSource={MOCK_ACTIVITY}
              rowKey="id"
              pagination={false}
              columns={[
                { title: 'Date', dataIndex: 'date' },
                {
                  title: 'Type',
                  dataIndex: 'type',
                  render: (type) => (
                    <Tag color={type === 'BUY' ? 'blue' : 'volcano'}>{type}</Tag>
                  )
                },
                { title: 'Symbol', dataIndex: 'symbol', render: (text) => <b>{text}</b> },
                { title: 'Qty', dataIndex: 'qty' },
                { title: 'Price', dataIndex: 'price', render: (val) => val.toFixed(2) },
                { title: 'Amount', dataIndex: 'amount', render: (val) => formatCurrency(val, baseCurrency === 'TWD' && val > 10000 ? 'TWD' : 'USD') }, // Simple mock logic
              ]}
            />
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default Dashboard;
