import React, { useState } from 'react';
import { Modal, Form, Input, Select, message } from 'antd';
import { stocksApi } from '../../api/stocks.api';
import type { Instrument } from '../../api/stocks.api';

const { Option } = Select;

interface AddInstrumentModalProps {
  open: boolean;
  onCancel: () => void;
  onSuccess: (instrument: Instrument) => void;
}

export const AddInstrumentModal: React.FC<AddInstrumentModalProps> = ({ open, onCancel, onSuccess }) => {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);

  const handleOk = async () => {
    try {
      const values = await form.validateFields();
      setLoading(true);
      
      const res = await stocksApi.addInstrument({
        ticker: values.ticker,
        nameZh: values.nameZh,
        nameEn: values.nameEn,
        market: values.market,
        exchange: values.exchange,
        currency: values.currency,
        assetType: values.assetType || 'STOCK',
      });

      message.success('成功建立標的');
      onSuccess(res);
      form.resetFields();
    } catch (error: unknown) {
      const err = error as { response?: { status: number } };
      if (err.response?.status === 409) {
        message.error('此標的已存在');
      } else {
        message.error('建立失敗，請稍後再試');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      title="新增投資標的"
      open={open}
      onOk={handleOk}
      onCancel={onCancel}
      confirmLoading={loading}
      destroyOnHidden
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{ market: 'TW', assetType: 'STOCK', currency: 'TWD' }}
      >
        <Form.Item
          name="ticker"
          label="股票代碼 (Ticker)"
          rules={[
            { required: true, message: '請輸入代碼' },
            { pattern: /^[A-Z0-9.-]{1,20}$/, message: '代碼長度 1-20 字，且格式正確' },
          ]}
        >
          <Input placeholder="例如: 2330 或 AAPL" />
        </Form.Item>

        <Form.Item
          name="nameZh"
          label="中文名稱"
        >
          <Input placeholder="例如: 台積電" />
        </Form.Item>

        <Form.Item
          name="nameEn"
          label="英文名稱"
        >
          <Input placeholder="例如: TSMC" />
        </Form.Item>

        <Form.Item
          name="market"
          label="市場"
          rules={[{ required: true }]}
        >
          <Select>
            <Option value="TW">台股 (TW)</Option>
            <Option value="US">美股 (US)</Option>
          </Select>
        </Form.Item>

        <Form.Item
          name="exchange"
          label="交易所"
          rules={[{ required: true, message: '請選擇交易所' }]}
        >
          <Select placeholder="選擇交易所">
            <Option value="TWSE">證交所 (TWSE)</Option>
            <Option value="TPEx">櫃買中心 (TPEx)</Option>
            <Option value="NASDAQ">那斯達克 (NASDAQ)</Option>
            <Option value="NYSE">紐約證交所 (NYSE)</Option>
          </Select>
        </Form.Item>

        <Form.Item
          name="currency"
          label="交易幣別"
          rules={[{ required: true }]}
        >
          <Select>
            <Option value="TWD">台幣 (TWD)</Option>
            <Option value="USD">美金 (USD)</Option>
          </Select>
        </Form.Item>

        <Form.Item
          name="assetType"
          label="資產類型"
        >
          <Select>
            <Option value="STOCK">股票 (STOCK)</Option>
            <Option value="ETF">指數股票型基金 (ETF)</Option>
          </Select>
        </Form.Item>
      </Form>
    </Modal>
  );
};
