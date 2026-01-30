import React, { useEffect } from 'react';
import { Typography, Card, Form, Select, Switch, Button, message } from 'antd';
import { useAuthStore } from '../../stores/auth.store';
import { usersApi } from '../../api/users.api';

const { Title } = Typography;
const { Option } = Select;

const Settings: React.FC = () => {
  const { user, checkAuth } = useAuthStore();
  const [form] = Form.useForm();

  useEffect(() => {
    if (user?.preferences) {
      form.setFieldsValue({
        currency: user.preferences.baseCurrency,
      });
    }
  }, [user, form]);

  const onFinish = async (values: any) => {
    try {
      await usersApi.updateSettings({
        baseCurrency: values.currency,
      });
      message.success('設定已更新');
      checkAuth();
    } catch (e) {
      message.error('更新失敗');
    }
  };

  return (
    <div style={{ maxWidth: 600 }}>
      <Title level={2}>設定</Title>
      <Card>
        <Form
          form={form}
          layout="vertical"
          initialValues={{ currency: 'TWD', theme: 'light', notifications: true }}
          onFinish={onFinish}
        >
          <Form.Item label="基準幣別" name="currency">
            <Select>
              <Option value="TWD">台幣 (TWD)</Option>
              <Option value="USD">美金 (USD)</Option>
            </Select>
          </Form.Item>

          <Form.Item label="主題" name="theme">
            <Select>
              <Option value="light">亮色</Option>
              <Option value="dark">暗色</Option>
            </Select>
          </Form.Item>

          <Form.Item label="Email 通知" name="notifications" valuePropName="checked">
            <Switch />
          </Form.Item>

          <Form.Item>
            <Button type="primary" htmlType="submit">
              儲存變更
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
};

export default Settings;
