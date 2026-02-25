import React, { useState } from 'react';
import { Modal, Steps, Form, DatePicker, InputNumber, Radio, Button, Row, Col, Typography, Tag, message } from 'antd';
import { InstrumentSearch } from '../../../components/common/InstrumentSearch';
import { stocksApi } from '../../../api/stocks.api';
import type { Instrument } from '../../../api/stocks.api';
import type { Trade } from '../../../api/portfolios.api';
import { usePortfolioStore } from '../../../stores/portfolio.store';
import dayjs from 'dayjs';
import { logger } from '../../../utils/logger';

const { Text } = Typography;

interface AddTradeModalProps {
  open: boolean;
  onCancel: () => void;
  onSuccess: () => void;
  trade?: Trade | null;
}

export const AddTradeModal: React.FC<AddTradeModalProps> = ({ open, onCancel, onSuccess, trade }) => {
  const [currentStep, setCurrentStep] = useState(0);
  const [selectedInstrument, setSelectedInstrument] = useState<Instrument | null>(null);
  const [form] = Form.useForm();
  const { addTrade, updateTrade, isLoading } = usePortfolioStore();

  React.useEffect(() => {
    if (open) {
      if (trade) {
        setCurrentStep(1);
        form.setFieldsValue({
          side: trade.side,
          date: dayjs(trade.tradeDate),
          quantity: Number(trade.quantity),
          price: Number(trade.price),
          fee: Number(trade.fee || 0),
        });
        // Fetch instrument details
        stocksApi.getInstrumentById(trade.instrumentId).then((inst) => {
          setSelectedInstrument(inst);
        }).catch(() => {
          // Fallback
          setSelectedInstrument({
            instrumentId: trade.instrumentId,
            ticker: 'Unknown',
            currency: trade.currency,
            nameZh: '',
            nameEn: '',
            exchange: '',
            market: '',
            assetType: '',
            symbolKey: '',
          });
        });
      } else {
        setCurrentStep(0);
        setSelectedInstrument(null);
        form.resetFields();
      }
    }
  }, [open, trade, form]);

  const handleInstrumentSelect = (instrument: Instrument) => {
    setSelectedInstrument(instrument);
    setCurrentStep(1);
  };

  const handleFinish = async (values: {
    side: 'BUY' | 'SELL';
    quantity: number;
    price: number;
    date: dayjs.Dayjs;
    fee?: number;
  }) => {
    if (!selectedInstrument) return;

    const payload = {
      instrumentId: selectedInstrument.instrumentId,
      side: values.side,
      quantity: String(values.quantity),
      price: String(values.price),
      currency: selectedInstrument.currency,
      tradeDate: values.date.format('YYYY-MM-DD'),
      fee: String(values.fee || 0),
    };

    try {
      if (trade) {
        await updateTrade(trade.tradeId, payload);
      } else {
        await addTrade(payload);
      }
      
      message.success(trade ? '交易已更新' : '交易新增成功');
      form.resetFields();
      setCurrentStep(0);
      setSelectedInstrument(null);
      onSuccess();
    } catch (error) {
      logger.error(trade ? 'Update trade failed' : 'Add trade failed', error);
      message.error(trade ? '更新交易失敗' : '新增交易失敗');
    }
  };

  const modalFooter = (
    <div style={{ display: 'flex', justifyContent: 'space-between', width: '100%' }}>
        {currentStep === 1 && (
            <Button onClick={() => setCurrentStep(0)}>上一步</Button>
        )}
        <div style={{ flex: 1 }}></div>
        {currentStep === 1 ? (
             <Button type="primary" onClick={() => form.submit()} loading={isLoading}>
                {trade ? '確認修改' : '確認新增'}
             </Button>
        ) : (
            <Button onClick={onCancel}>取消</Button>
        )}
    </div>
  );

  return (
    <Modal
      title={trade ? "編輯交易" : "新增交易"}
      open={open}
      onCancel={onCancel}
      footer={modalFooter}
      destroyOnHidden
      width={600}
    >
      <Steps
        current={currentStep}
        items={[
          { title: '選擇股票' },
          { title: '交易明細' },
        ]}
        style={{ marginBottom: 24 }}
      />

      <Form
        form={form}
        layout="vertical"
        initialValues={{ date: dayjs(), side: 'BUY', fee: 0 }}
        onFinish={handleFinish}
      >
        {currentStep === 0 ? (
          <div style={{ padding: '20px 0', minHeight: 200 }}>
            <Text style={{ display: 'block', marginBottom: 8 }}>搜尋代號或名稱：</Text>
            <InstrumentSearch onSelect={handleInstrumentSelect} />
          </div>
        ) : (
          selectedInstrument && (
            <>
              <div style={{ marginBottom: 16, padding: 12, background: '#f5f5f5', borderRadius: 4 }}>
                <Text strong>{selectedInstrument.ticker} - {selectedInstrument.nameZh || selectedInstrument.nameEn}</Text>
                <div><Tag>{selectedInstrument.exchange}</Tag></div>
              </div>

              <Row gutter={16}>
                <Col span={12}>
                  <Form.Item name="side" label="方向" rules={[{ required: true }]}>
                    <Radio.Group buttonStyle="solid">
                      <Radio.Button value="BUY">買入</Radio.Button>
                      <Radio.Button value="SELL">賣出</Radio.Button>
                    </Radio.Group>
                  </Form.Item>
                </Col>
                <Col span={12}>
                  <Form.Item name="date" label="日期" rules={[{ required: true }]}>
                    <DatePicker style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
              </Row>

              <Row gutter={16}>
                <Col span={12}>
                  <Form.Item name="quantity" label="數量 (股)" rules={[{ required: true }]}>
                    <InputNumber style={{ width: '100%' }} min={0.0001} />
                  </Form.Item>
                </Col>
                <Col span={12}>
                  <Form.Item name="price" label={`價格 (${selectedInstrument.currency})`} rules={[{ required: true }]}>
                    <InputNumber style={{ width: '100%' }} min={0} />
                  </Form.Item>
                </Col>
              </Row>

              <Row gutter={16}>
                <Col span={12}>
                  <Form.Item name="fee" label="手續費">
                    <InputNumber style={{ width: '100%' }} min={0} />
                  </Form.Item>
                </Col>
              </Row>
            </>
          )
        )}
      </Form>
    </Modal>
  );
};
