import React from 'react';
import { Typography, Card, Form, Select, Switch, Button, message } from 'antd';

const { Title } = Typography;
const { Option } = Select;

const Settings: React.FC = () => {
  const onFinish = (values: any) => {
    console.log('Success:', values);
    message.success('Settings updated');
  };

  return (
    <div style={{ maxWidth: 600 }}>
      <Title level={2}>Settings</Title>
      <Card>
        <Form
          layout="vertical"
          initialValues={{ currency: 'TWD', theme: 'light', notifications: true }}
          onFinish={onFinish}
        >
          <Form.Item label="Base Currency" name="currency">
            <Select>
              <Option value="TWD">TWD (New Taiwan Dollar)</Option>
              <Option value="USD">USD (US Dollar)</Option>
            </Select>
          </Form.Item>

          <Form.Item label="Theme" name="theme">
            <Select>
              <Option value="light">Light</Option>
              <Option value="dark">Dark</Option>
            </Select>
          </Form.Item>

          <Form.Item label="Email Notifications" name="notifications" valuePropName="checked">
            <Switch />
          </Form.Item>

          <Form.Item>
            <Button type="primary" htmlType="submit">
              Save Changes
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
};

export default Settings;
