import React, { useEffect, useState } from 'react';
import { Table, Card, Row, Col, Statistic, Button, Tag, Typography, Modal, Steps, Form, DatePicker, InputNumber, Radio, message } from 'antd';
import { PlusOutlined, ArrowUpOutlined, ArrowDownOutlined, ReloadOutlined } from '@ant-design/icons';
import { usePortfolioStore } from '../../stores/portfolio.store';
import { formatCurrency } from '../../utils/format';
import { InstrumentSearch } from '../../components/common/InstrumentSearch';
import type { Instrument } from '../../api/stocks.api';
import dayjs from 'dayjs';

const { Title, Text } = Typography;

const Portfolio: React.FC = () => {
  const { summary, positions, isLoading, fetchPortfolioData } = usePortfolioStore();
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [currentStep, setCurrentStep] = useState(0);
  const [selectedInstrument, setSelectedInstrument] = useState<Instrument | null>(null);
  const [form] = Form.useForm();

  useEffect(() => {
    fetchPortfolioData();
  }, [fetchPortfolioData]);

  const handleInstrumentSelect = (instrument: Instrument) => {
    setSelectedInstrument(instrument);
    setCurrentStep(1); // Move to details step
  };

  const handleFinish = (values: any) => {
    console.log('Form values:', { ...values, instrument: selectedInstrument });
    message.success('Trade added successfully (Mock)');
    setIsModalOpen(false);
    setCurrentStep(0);
    setSelectedInstrument(null);
    form.resetFields();
    // Refresh data...
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

      {/* Add Trade Modal */}
      <Modal
        title="Add New Trade"
        open={isModalOpen}
        onCancel={() => setIsModalOpen(false)}
        footer={null}
        destroyOnClose
      >
        <Steps
          current={currentStep}
          items={[
            { title: 'Select Stock' },
            { title: 'Trade Details' },
          ]}
          style={{ marginBottom: 24 }}
        />

        {currentStep === 0 && (
          <div style={{ padding: '20px 0', minHeight: 200 }}>
            <Text style={{ display: 'block', marginBottom: 8 }}>Search by symbol or name:</Text>
            <InstrumentSearch onSelect={handleInstrumentSelect} />
          </div>
        )}

        {currentStep === 1 && selectedInstrument && (
          <Form
            form={form}
            layout="vertical"
            initialValues={{ date: dayjs(), side: 'BUY', currency: selectedInstrument.currency }}
            onFinish={handleFinish}
          >
            <div style={{ marginBottom: 16, padding: 12, background: '#f5f5f5', borderRadius: 4 }}>
              <Text strong>{selectedInstrument.symbol} - {selectedInstrument.name}</Text>
              <div><Tag>{selectedInstrument.exchange}</Tag></div>
            </div>

            <Row gutter={16}>
              <Col span={12}>
                <Form.Item name="side" label="Side" rules={[{ required: true }]}>
                  <Radio.Group buttonStyle="solid">
                    <Radio.Button value="BUY">Buy</Radio.Button>
                    <Radio.Button value="SELL">Sell</Radio.Button>
                  </Radio.Group>
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item name="date" label="Date" rules={[{ required: true }]}>
                  <DatePicker style={{ width: '100%' }} />
                </Form.Item>
              </Col>
            </Row>

            <Row gutter={16}>
              <Col span={12}>
                <Form.Item name="quantity" label="Quantity" rules={[{ required: true }]}>
                  <InputNumber style={{ width: '100%' }} min={0.0001} />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item name="price" label={`Price (${selectedInstrument.currency})`} rules={[{ required: true }]}>
                  <InputNumber style={{ width: '100%' }} min={0} />
                </Form.Item>
              </Col>
            </Row>

            <Row gutter={16}>
              <Col span={12}>
                <Form.Item name="fee" label="Commission (Fee)">
                  <InputNumber style={{ width: '100%' }} min={0} defaultValue={0} />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item name="tax" label="Tax">
                  <InputNumber style={{ width: '100%' }} min={0} defaultValue={0} />
                </Form.Item>
              </Col>
            </Row>

            <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 16 }}>
              <Button onClick={() => setCurrentStep(0)}>Back</Button>
              <Button type="primary" htmlType="submit">Confirm Trade</Button>
            </div>
          </Form>
        )}
      </Modal>
    </div>
  );
};

export default Portfolio;