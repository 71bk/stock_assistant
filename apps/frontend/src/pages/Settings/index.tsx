import React, { useEffect, useState } from 'react';
import { Typography, Card, Form, Select, Switch, Button, message, Divider, Input } from 'antd';
import { useAuthStore } from '../../stores/auth.store';
import { usersApi } from '../../api/users.api';
import { adminApi } from '../../api/admin.api';

const { Title, Text } = Typography;
const { Option } = Select;

const Settings: React.FC = () => {
  const { user, checkAuth } = useAuthStore();
  const [form] = Form.useForm();
  const [adminKey, setAdminKey] = useState('');
  const [isSyncing, setIsSyncing] = useState(false);

  useEffect(() => {
    if (user) {
      form.setFieldsValue({
        currency: user.baseCurrency,
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

  const handleSyncInstruments = async () => {
    setIsSyncing(true);
    try {
      const res = await adminApi.syncInstruments(adminKey);
      const { added, skipped } = res;
      message.success(`標的同步成功！新增：${added} 筆，略過：${skipped} 筆`);
    } catch (e) {
      message.error('同步失敗，請檢查 Admin Key 是否正確');
    } finally {
      setIsSyncing(false);
    }
  };

  const handleSyncWarrants = async () => {
    setIsSyncing(true);
    try {
      const res = await adminApi.syncWarrants(adminKey);
      const { added, skipped } = res;
      message.success(`權證同步成功！新增：${added} 筆，略過：${skipped} 筆`);
    } catch (e) {
      message.error('權證同步失敗，請檢查 Admin Key 是否正確');
    } finally {
      setIsSyncing(false);
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

        <Divider />
        
        <Title level={4}>系統管理 (Admin)</Title>
        <div style={{ marginTop: 16 }}>
          <Text type="secondary" style={{ display: 'block', marginBottom: 8 }}>
            同步市場標的 (從 Fugle 匯入台股清單)
          </Text>
          <div style={{ display: 'flex', gap: 8 }}>
            <Input.Password
              placeholder="輸入 Admin Key (若有設定)"
              value={adminKey}
              onChange={(e) => setAdminKey(e.target.value)}
              style={{ maxWidth: 200 }}
            />
            <Button
              onClick={handleSyncInstruments}
              loading={isSyncing}
              danger
            >
              同步標的資料
            </Button>
            <Button
              onClick={handleSyncWarrants}
              loading={isSyncing}
              danger
            >
              同步權證資料
            </Button>
          </div>
        </div>
      </Card>
    </div>
  );
};

export default Settings;
