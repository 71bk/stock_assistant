import React, { useEffect, useState } from 'react';
import { Table, Card, Row, Col, Statistic, Button, Tag, Typography } from 'antd';
import { PlusOutlined, ArrowUpOutlined, ArrowDownOutlined, ReloadOutlined } from '@ant-design/icons';
import { usePortfolioStore } from '../../stores/portfolio.store';
import { formatCurrency } from '../../utils/format';
import { AddTradeModal } from './components/AddTradeModal';

const { Title } = Typography;

const Portfolio: React.FC = () => {
  const { summary, positions, isLoading, fetchPortfolioData } = usePortfolioStore();
  const [isModalOpen, setIsModalOpen] = useState(false);

  useEffect(() => {
    fetchPortfolioData();
  }, [fetchPortfolioData]);

  const handleTradeSuccess = () => {
    fetchPortfolioData();
    setIsModalOpen(false);
  };

  const renderKPI = () => (
    <Row gutter={16} style={{ marginBottom: 24 }}>
      <Col xs={24} sm={8}>
        <Card>
          <Statistic
            title="Total Market Value"
            value={summary?.totalMarketValue}
            precision={0}
            prefix={summary?.baseCurrency === 'USD' ? '$' : 'NT$'}
            formatter={(val) => formatCurrency(Number(val), summary?.baseCurrency)}
          />
        </Card>
      </Col>
      <Col xs={24} sm={8}>
        <Card>
          <Statistic
            title="Total Cost"
            value={summary?.totalCost}
            precision={0}
            prefix={summary?.baseCurrency === 'USD' ? '$' : 'NT$'}
            formatter={(val) => formatCurrency(Number(val), summary?.baseCurrency)}
          />
        </Card>
      </Col>
      <Col xs={24} sm={8}>
        <Card>
          <Statistic
            title="Total P/L"
            value={summary?.totalPnl}
            precision={0}
            valueStyle={{ color: (summary?.totalPnl || 0) >= 0 ? '#3f8600' : '#cf1322' }}
            prefix={(summary?.totalPnl || 0) >= 0 ? <ArrowUpOutlined /> : <ArrowDownOutlined />}
            suffix={`(${summary?.totalPnlPercent}%)`}
            formatter={(val) => formatCurrency(Number(val), summary?.baseCurrency)}
          />
        </Card>
      </Col>
    </Row>
  );

  const columns = [
    {
      title: 'Instrument',
      dataIndex: 'symbol',
      key: 'symbol',
      render: (text: string, record: any) => (
        <div>
          <div style={{ fontWeight: 'bold' }}>{text}</div>
          <div style={{ fontSize: 12, color: '#888' }}>{record.name}</div>
          <Tag color={record.market === 'US' ? 'blue' : 'green'} style={{ marginTop: 4 }}>{record.market}</Tag>
        </div>
      ),
    },
    {
      title: 'Qty',
      dataIndex: 'quantity',
      key: 'quantity',
      align: 'right' as const,
      render: (val: number) => val.toLocaleString(),
    },
    {
      title: 'Avg Cost',
      dataIndex: 'avgCost',
      key: 'avgCost',
      align: 'right' as const,
      render: (val: number, record: any) => formatCurrency(val, record.currency),
    },
    {
      title: 'Price',
      dataIndex: 'currentPrice',
      key: 'currentPrice',
      align: 'right' as const,
      render: (val: number, record: any) => (
        <span style={{ fontWeight: 'bold' }}>{formatCurrency(val, record.currency)}</span>
      ),
    },
    {
      title: 'Value',
      dataIndex: 'currentValue',
      key: 'currentValue',
      align: 'right' as const,
      render: (val: number, record: any) => formatCurrency(val, record.currency), // In list, show native currency
    },
    {
      title: 'Unrealized P/L',
      dataIndex: 'unrealizedPnl',
      key: 'unrealizedPnl',
      align: 'right' as const,
      render: (val: number, record: any) => (
        <div style={{ color: val >= 0 ? '#3f8600' : '#cf1322' }}>
          <div>{formatCurrency(val, record.currency)}</div>
          <div style={{ fontSize: 12 }}>{record.unrealizedPnlPercent}%</div>
        </div>
      ),
    },
    {
      title: 'Actions',
      key: 'actions',
      render: () => <Button size="small">Details</Button>,
    },
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Title level={2} style={{ margin: 0 }}>My Portfolio</Title>
        <div>
          <Button icon={<ReloadOutlined />} onClick={() => fetchPortfolioData()} style={{ marginRight: 8 }}>Refresh</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setIsModalOpen(true)}>Add Trade</Button>
        </div>
      </div>

      {renderKPI()}

      <Card bodyStyle={{ padding: 0 }}>
        <Table
          dataSource={positions}
          columns={columns}
          rowKey="instrumentId"
          loading={isLoading}
          pagination={false}
        />
      </Card>

      <AddTradeModal
        open={isModalOpen}
        onCancel={() => setIsModalOpen(false)}
        onSuccess={handleTradeSuccess}
      />
    </div>
  );
};

export default Portfolio;
