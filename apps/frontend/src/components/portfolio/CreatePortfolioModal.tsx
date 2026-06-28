import React, { useState } from 'react';
import { Modal, Form, Input, Select } from 'antd';
import { usePortfolioStore } from '../../stores/portfolio.store';
import { antd } from '../../utils/antd-globals';

interface FormValues {
  name: string;
  baseCurrency: string;
}

/**
 * Global create-portfolio modal. Driven by portfolio.store.createModalOpen so any
 * entry point (Portfolio page button / empty state, or an OCR upload that needs a
 * portfolio) can open it; on success the awaited requirePortfolio() resumes.
 */
export const CreatePortfolioModal: React.FC = () => {
  const open = usePortfolioStore((s) => s.createModalOpen);
  const createPortfolio = usePortfolioStore((s) => s.createPortfolio);
  const cancel = usePortfolioStore((s) => s.cancelCreatePortfolio);
  const [form] = Form.useForm<FormValues>();
  const [submitting, setSubmitting] = useState(false);

  const handleOk = async () => {
    let values: FormValues;
    try {
      values = await form.validateFields();
    } catch {
      return; // validation errors are shown inline
    }
    setSubmitting(true);
    try {
      await createPortfolio(values.name.trim(), values.baseCurrency);
      antd.message.success('投資組合已建立');
      form.resetFields();
    } catch {
      antd.message.error('建立投資組合失敗，請稍後再試');
    } finally {
      setSubmitting(false);
    }
  };

  const handleCancel = () => {
    form.resetFields();
    cancel();
  };

  return (
    <Modal
      title="建立投資組合"
      open={open}
      onOk={handleOk}
      onCancel={handleCancel}
      confirmLoading={submitting}
      okText="建立"
      cancelText="取消"
      destroyOnHidden
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{ name: '我的投資組合', baseCurrency: 'TWD' }}
      >
        <Form.Item
          name="name"
          label="名稱"
          rules={[{ required: true, message: '請輸入投資組合名稱' }]}
        >
          <Input placeholder="例如：富邦證券、美股帳戶" maxLength={50} />
        </Form.Item>
        <Form.Item
          name="baseCurrency"
          label="基準幣別"
          rules={[{ required: true, message: '請選擇基準幣別' }]}
          extra="用於整體市值、損益的計算基準"
        >
          <Select
            options={[
              { value: 'TWD', label: 'TWD 新台幣' },
              { value: 'USD', label: 'USD 美元' },
            ]}
          />
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default CreatePortfolioModal;
