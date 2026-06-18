import React, { useEffect, useState } from 'react';
import { Typography, Card, Table, Tag, Button, Space, Popconfirm, Skeleton } from 'antd';
import { EditOutlined, DeleteOutlined } from '@ant-design/icons';
import { usePortfolioStore } from '../../stores/portfolio.store';
import { formatCurrency } from '../../utils/format';
import { AddTradeModal } from '../Portfolio/components/AddTradeModal';
import { PageContainer } from '../../components/layout/PageContainer';
import { ErrorState } from '../../components/common/ErrorState';
import type { Trade } from '../../api/portfolios.api';

const { Title } = Typography;

const Trades: React.FC = () => {
  const { trades, isLoading, error, fetchTrades, deleteTrade } = usePortfolioStore();
  const [editingTrade, setEditingTrade] = useState<Trade | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);

  useEffect(() => {
    fetchTrades();
  }, [fetchTrades]);

  const handleEdit = (trade: Trade) => {
    setEditingTrade(trade);
    setIsModalOpen(true);
  };

  const handleModalClose = () => {
    setIsModalOpen(false);
    setEditingTrade(null);
  };

  if (error) {
    return (
      <PageContainer>
        <ErrorState
          status="500"
          title="載入交易紀錄失敗"
          message={error}
        />
      </PageContainer>
    );
  }

  return (
    <PageContainer>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Title level={2} style={{ margin: 0 }}>交易紀錄</Title>
        <Button type="primary" onClick={() => setIsModalOpen(true)}>新增交易</Button>
      </div>
      
      <Card>
        {isLoading ? (
          <div style={{ padding: 20 }}>
            <Skeleton active paragraph={{ rows: 10 }} />
          </div>
        ) : (
          <Table
            dataSource={trades}
            rowKey="tradeId"
            columns={[
              { title: '日期', dataIndex: 'tradeDate' },
              {
                title: '類型',
                dataIndex: 'side',
                render: (type) => (
                  <Tag color={type === 'BUY' ? 'blue' : 'volcano'}>{type}</Tag>
                )
              },
              {
                title: '代號',
                dataIndex: 'instrumentId',
                render: (text, record) => {
                  const ticker = record.ticker || text;
                  const name = record.nameZh || record.nameEn;
                  return (
                    <div>
                      <div style={{ fontWeight: 'bold' }}>{ticker}</div>
                      {name && <div style={{ fontSize: 12, color: '#888' }}>{name}</div>}
                    </div>
                  );
                },
              },
              { title: '數量', dataIndex: 'quantity' },
              { title: '價格', dataIndex: 'price', render: (val) => Number(val).toFixed(2) },
              { title: '金額', dataIndex: 'netAmount', render: (val, record: Trade) => formatCurrency(Number(val || (Number(record.price) * Number(record.quantity))), record.currency) },
              {
                title: '操作',
                key: 'actions',
                render: (_: unknown, record: Trade) => (
                  <Space>
                    <Button type="text" icon={<EditOutlined />} onClick={() => handleEdit(record)} />
                    <Popconfirm title="確定刪除?" onConfirm={() => deleteTrade(record.tradeId)}>
                      <Button type="text" danger icon={<DeleteOutlined />} />
                    </Popconfirm>
                  </Space>
                ),
              },
            ]}
          />
        )}
      </Card>

      <AddTradeModal
        open={isModalOpen}
        onCancel={handleModalClose}
        onSuccess={() => {
          handleModalClose();
          fetchTrades();
        }}
        trade={editingTrade}
      />
    </PageContainer>
  );
};

export default Trades;
