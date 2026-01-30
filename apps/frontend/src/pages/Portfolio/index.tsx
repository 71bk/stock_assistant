import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Table, Card, Row, Col, Statistic, Button, Tag, Typography } from 'antd';
import { PlusOutlined, ArrowUpOutlined, ArrowDownOutlined, ReloadOutlined } from '@ant-design/icons';
import { usePortfolioStore } from '../../stores/portfolio.store';
import { formatCurrency } from '../../utils/format';
import { AddTradeModal } from './components/AddTradeModal';

const { Title } = Typography;

const Portfolio: React.FC = () => {
  const navigate = useNavigate();
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
            title="總市值"
            value={summary?.totalMarketValue || 0}
            precision={0}
            prefix={summary?.baseCurrency === 'USD' ? '$' : 'NT$'}
            formatter={(val) => formatCurrency(Number(val), summary?.baseCurrency)}
          />
        </Card>
      </Col>
      <Col xs={24} sm={8}>
        <Card>
          <Statistic
            title="總成本"
            value={summary?.totalCost || 0}
            precision={0}
            prefix={summary?.baseCurrency === 'USD' ? '$' : 'NT$'}
            formatter={(val) => formatCurrency(Number(val), summary?.baseCurrency)}
          />
        </Card>
      </Col>
      <Col xs={24} sm={8}>
        <Card>
          <Statistic
            title="總損益"
            value={summary?.totalPnl || 0}
            precision={0}
            styles={{ content: { color: (summary?.totalPnl || 0) >= 0 ? '#3f8600' : '#cf1322' } }}
            prefix={(summary?.totalPnl || 0) >= 0 ? <ArrowUpOutlined /> : <ArrowDownOutlined />}
            suffix={`(${summary?.totalPnlPercent || 0}%)`}
            formatter={(val) => formatCurrency(Number(val), summary?.baseCurrency)}
          />
        </Card>
      </Col>
    </Row>
  );

  const columns = [
    {
      title: '商品',
      dataIndex: 'ticker',
      key: 'ticker',
      render: (text: string, record: any) => (
        <div>
          <div style={{ fontWeight: 'bold' }}>{text}</div>
          <div style={{ fontSize: 12, color: '#888' }}>{record.name}</div>
          <Tag color={record.market === 'US' ? 'blue' : 'green'} style={{ marginTop: 4 }}>{record.market}</Tag>
        </div>
      ),
    },
    {
      title: '持股數',
      dataIndex: 'totalQuantity',
      key: 'totalQuantity',
      align: 'right' as const,
      render: (val: number) => val?.toLocaleString() ?? '-',
    },
    {
      title: '平均成本',
      dataIndex: 'avgCostNative',
      key: 'avgCostNative',
      align: 'right' as const,
      render: (val: number, record: any) => val != null ? formatCurrency(val, record.currency) : '-',
    },
    {
      title: '現價',
      dataIndex: 'currentPrice',
      key: 'currentPrice',
      align: 'right' as const,
      render: (val: number, record: any) => (
        <span style={{ fontWeight: 'bold' }}>{val != null ? formatCurrency(val, record.currency) : '-'}</span>
      ),
    },
    {
      title: '市值',
      dataIndex: 'currentValue',
      key: 'currentValue',
      align: 'right' as const,
      render: (val: number, record: any) => val != null ? formatCurrency(val, record.currency) : '-',
    },
    {
      title: '未實現損益',
      dataIndex: 'unrealizedPnl',
      key: 'unrealizedPnl',
      align: 'right' as const,
      render: (val: number, record: any) => (
        <div style={{ color: (val || 0) >= 0 ? '#3f8600' : '#cf1322' }}>
          <div>{val != null ? formatCurrency(val, record.currency) : '-'}</div>
          <div style={{ fontSize: 12 }}>{record.unrealizedPnlPercent ?? '-'}%</div>
        </div>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      render: () => (
        <Button
          size="small"
          onClick={() => navigate('/trades')}
        >
          詳情
        </Button>
      ),
    },
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Title level={2} style={{ margin: 0 }}>我的投資組合</Title>
        <div>
          <Button icon={<ReloadOutlined />} onClick={() => fetchPortfolioData()} style={{ marginRight: 8 }}>重新整理</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setIsModalOpen(true)}>新增交易</Button>
        </div>
      </div>

      {renderKPI()}

      <Card styles={{ body: { padding: 0 } }}>
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
