import React, { useState } from 'react';
import { Modal, Steps, Form, DatePicker, InputNumber, Radio, Button, Row, Col, Typography, Tag, message } from 'antd';
import { InstrumentSearch } from '../../../components/common/InstrumentSearch';
import type { Instrument } from '../../../types/domain'; // Assuming Instrument type is in domain or api
import { usePortfolioStore } from '../../../stores/portfolio.store';
import dayjs from 'dayjs';

const { Text } = Typography;

interface AddTradeModalProps {
  open: boolean;
  onCancel: () => void;
  onSuccess: () => void;
}

export const AddTradeModal: React.FC<AddTradeModalProps> = ({ open, onCancel, onSuccess }) => {
  const [currentStep, setCurrentStep] = useState(0);
  const [selectedInstrument, setSelectedInstrument] = useState<Instrument | null>(null);
  const [form] = Form.useForm();
  const { addTrade, isLoading } = usePortfolioStore();

  const handleInstrumentSelect = (instrument: Instrument) => {
    setSelectedInstrument(instrument);
    setCurrentStep(1);
  };

  const handleFinish = async (values: any) => {
    if (!selectedInstrument) return;

    try {
      await addTrade({
        instrumentId: selectedInstrument.id,
        symbol: selectedInstrument.symbol,
        side: values.side,
        quantity: values.quantity,
        price: values.price,
        currency: selectedInstrument.currency,
        tradeDate: values.date.format('YYYY-MM-DD'),
        fees: values.fee || 0,
      });
      
      message.success('Trade added successfully');
      form.resetFields();
      setCurrentStep(0);
      setSelectedInstrument(null);
      onSuccess();
    } catch (error) {
      console.error(error);
      message.error('Failed to add trade');
    }
  };

  const modalFooter = (
    <div style={{ display: 'flex', justifyContent: 'space-between', width: '100%' }}>
        {currentStep === 1 && (
            <Button onClick={() => setCurrentStep(0)}>Back</Button>
        )}
        <div style={{ flex: 1 }}></div>
        {currentStep === 1 ? (
             <Button type="primary" onClick={() => form.submit()} loading={isLoading}>
                Confirm Trade
             </Button>
        ) : (
            <Button onClick={onCancel}>Cancel</Button>
        )}
    </div>
  );

  return (
    <Modal
      title="Add New Trade"
      open={open}
      onCancel={onCancel}
      footer={modalFooter}
      destroyOnClose
      width={600}
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
            {/* Tax logic can be added here if needed */}
          </Row>
        </Form>
      )}
    </Modal>
  );
};
