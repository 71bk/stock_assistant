import React, { useEffect } from 'react';
import { Row, Col, Card, Statistic, Table, Typography, Tag, Skeleton } from 'antd';
import { ArrowUpOutlined, ArrowDownOutlined } from '@ant-design/icons';
import { Pie, Area } from '@ant-design/plots';
import { useAuthStore } from '../../stores/auth.store';
import { usePortfolioStore } from '../../stores/portfolio.store';
import { formatCurrency } from '../../utils/format';
import { PageContainer } from '../../components/layout/PageContainer';
import type { Trade } from '../../api/portfolios.api';

const { Title } = Typography;

const Dashboard: React.FC = () => {
  const { user } = useAuthStore();
  const { summary, positions, recentTrades, valuations, isLoading, fetchPortfolioData, fetchRecentTrades, fetchPortfolioValuations } = usePortfolioStore();
  const baseCurrency = user?.baseCurrency || 'TWD';

  useEffect(() => {
    fetchPortfolioData(); // Fetch summary AND positions
    fetchRecentTrades();
    fetchPortfolioValuations();
  }, [fetchPortfolioData, fetchRecentTrades, fetchPortfolioValuations]);

  // Chart Configs
  // Calculate value for pie chart (fallback to cost if market value missing)
  const pieData = positions.map((p) => ({
    ...p,
    value: p.currentValue || (p.totalQuantity * p.avgCostNative) || 0,
  }));

  const pieConfig = {
    data: pieData,
    angleField: 'value',
    colorField: 'ticker',
    radius: 0.8,
    label: {
      text: (d: { ticker: string; value: number }) => `${d.ticker}\n${(d.value / (summary?.totalMarketValue || 1) * 100).toFixed(1)}%`,
      position: 'spider',
    },
    legend: {
      color: {
        title: false,
        position: 'right',
        rowPadding: 5,
      },
    },
  };

  const areaConfig = {
    data: valuations.map(v => ({
      date: v.date,
      value: v.totalValue
    })),
    xField: 'date',
    yField: 'value',
    style: {
      fill: 'linear-gradient(-90deg, white 0%, #1677ff 100%)',
    },
    // Add tooltip formatting
    tooltip: {
      formatter: (datum: any) => {
        return { name: '總資產', value: formatCurrency(datum.value, baseCurrency) };
      },
    },
  };

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
    <PageContainer>
      <div style={{ marginBottom: 24 }}>
        <Title level={2}>總覽</Title>
      </div>

      {/* KPI Cards */}
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={8}>
          <Card>
            <Statistic
              title="總資產"
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
              title="總損益"
              value={totalPnl}
              precision={0}
              styles={{ content: { color: totalPnl >= 0 ? '#3f8600' : '#cf1322' } }}
              prefix={totalPnl >= 0 ? <ArrowUpOutlined /> : <ArrowDownOutlined />}
              formatter={(val) => formatCurrency(Number(val), baseCurrency)}
            />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card>
            <Statistic
              title="報酬率"
              value={roi}
              precision={2}
              styles={{ content: { color: roi >= 0 ? '#3f8600' : '#cf1322' } }}
              suffix="%"
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 24 }}>
        {/* Asset Trend */}
        <Col xs={24} lg={14}>
          <Card title="資產趨勢 (30天)">
            <div style={{ height: 300 }}>
              <Area {...areaConfig} />
            </div>
          </Card>
        </Col>

        {/* Asset Allocation */}
        <Col xs={24} lg={10}>
          <Card title="資產分佈">
            <div style={{ height: 300 }}>
              <Pie {...pieConfig} />
            </div>
          </Card>
        </Col>
      </Row>

      {/* Recent Activity */}
      <Row gutter={[16, 16]} style={{ marginTop: 24 }}>
        <Col span={24}>
          <Card title="近期活動">
            <Table
              dataSource={recentTrades}
              rowKey="tradeId"
              pagination={false}
              columns={[
                { title: '日期', dataIndex: 'tradeDate' },
                {
                  title: '類型',
                  dataIndex: 'side',
                  render: (type) => (
                    <Tag color={type === 'BUY' ? 'blue' : 'volcano'}>{type}</Tag>
                  )
                },
                { title: '代號', dataIndex: 'instrumentId', render: (text) => <b>{text}</b> },
                { title: '數量', dataIndex: 'quantity' },
                { title: '價格', dataIndex: 'price', render: (val: string | number) => Number(val).toFixed(2) },
                { title: '金額', dataIndex: 'netAmount', render: (val: string | number, record: Trade) => formatCurrency(Number(val || (Number(record.price) * Number(record.quantity))), record.currency) },
              ]}
            />
          </Card>
        </Col>
      </Row>
    </PageContainer>
  );
};

export default Dashboard;
