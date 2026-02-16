import React, { useState } from 'react';
import { Card, Typography, Row, Col, Statistic, Button, message, Input, Space, Divider, Alert } from 'antd';
import { SyncOutlined, ReloadOutlined, DatabaseOutlined, SaveOutlined, KeyOutlined } from '@ant-design/icons';
import { adminApi, type SyncResult } from '../../api/admin.api';
import { PageContainer } from '../../components/layout/PageContainer';

const { Title, Text } = Typography;

export const Dashboard: React.FC = () => {
  const [loading, setLoading] = useState<Record<string, boolean>>({});
  const [apiKey, setApiKey] = useState<string>(() => localStorage.getItem('admin_api_key') || '');
  const [result, setResult] = useState<{
    type: 'success' | 'error';
    message: string;
    details?: string;
  } | null>(null);

  const saveApiKey = () => {
    localStorage.setItem('admin_api_key', apiKey);
    message.success('Admin API Key 已儲存');
  };

  const handleAction = async (action: string, apiCall: () => Promise<any>) => {
    setLoading(prev => ({ ...prev, [action]: true }));
    setResult(null);
    try {
      const res = await apiCall();
      console.log(`${action} result:`, res);
      
      let msg = '操作成功';
      let details = '';

      if (action === 'syncInstruments') {
        const data = res as SyncResult;
        msg = `同步完成：新增 ${data.added} 筆，略過 ${data.skipped} 筆`;
      } else if (action === 'syncWarrants') {
        const data = res as SyncResult;
        msg = `同步完成：新增 ${data.added} 筆，略過 ${data.skipped} 筆`;
      } else if (action === 'rebuildPositions') {
        msg = `持倉重算完成`;
        details = JSON.stringify(res, null, 2);
      } else if (action === 'snapshotValuations') {
        msg = `估值快照完成`;
        details = JSON.stringify(res, null, 2);
      }

      setResult({ type: 'success', message: msg, details });
      message.success(msg);
    } catch (err: any) {
      console.error(`${action} failed:`, err);
      const errMsg = err.message || '操作失敗';
      setResult({ type: 'error', message: errMsg });
      message.error(errMsg);
    } finally {
      setLoading(prev => ({ ...prev, [action]: false }));
    }
  };

  return (
    <PageContainer title="管理員儀表板">
      <Row gutter={[16, 16]}>
        <Col span={24}>
          <Card title="API Key 設定 (若後端有啟用)" bordered={false}>
            <Space.Compact style={{ width: '100%' }}>
              <Input.Password
                prefix={<KeyOutlined />}
                placeholder="輸入 Admin API Key (X-Admin-Key)"
                value={apiKey}
                onChange={(e) => setApiKey(e.target.value)}
              />
              <Button type="primary" icon={<SaveOutlined />} onClick={saveApiKey}>
                儲存
              </Button>
            </Space.Compact>
            <Text type="secondary" style={{ fontSize: 12, display: 'block', marginTop: 8 }}>
              某些敏感操作可能需要額外的 API Key 驗證。此 Key 將儲存於瀏覽器 LocalStorage。
            </Text>
          </Card>
        </Col>

        {result && (
          <Col span={24}>
            <Alert
              message={result.message}
              description={result.details && <pre style={{ fontSize: 12, marginTop: 8 }}>{result.details}</pre>}
              type={result.type}
              showIcon
              closable
              onClose={() => setResult(null)}
            />
          </Col>
        )}

        <Col span={24}>
          <Title level={4}>資料同步</Title>
        </Col>
        
        <Col xs={24} sm={12}>
          <Card>
            <Statistic
              title="股票主檔同步 (Fugle)"
              value="Sync"
              formatter={() => (
                <Button 
                  type="primary" 
                  icon={<SyncOutlined spin={loading['syncInstruments']} />} 
                  loading={loading['syncInstruments']}
                  onClick={() => handleAction('syncInstruments', () => adminApi.syncInstruments(apiKey))}
                >
                  開始同步股票
                </Button>
              )}
            />
            <Text type="secondary" style={{ marginTop: 8, display: 'block' }}>
              同步台股上市櫃股票、ETF 基本資料。
            </Text>
          </Card>
        </Col>

        <Col xs={24} sm={12}>
          <Card>
            <Statistic
              title="權證主檔同步 (TPEx + TWSE)"
              value="Sync"
              formatter={() => (
                <Button 
                  type="primary" 
                  icon={<SyncOutlined spin={loading['syncWarrants']} />} 
                  loading={loading['syncWarrants']}
                  onClick={() => handleAction('syncWarrants', () => adminApi.syncWarrants(apiKey))}
                >
                  開始同步權證
                </Button>
              )}
            />
            <Text type="secondary" style={{ marginTop: 8, display: 'block' }}>
              同步上市櫃權證基本資料與到期日。
            </Text>
          </Card>
        </Col>

        <Col span={24}>
          <Divider />
          <Title level={4}>維護工具</Title>
        </Col>

        <Col xs={24} sm={12}>
          <Card>
            <Statistic
              title="持倉重算 (Positions Rebuild)"
              value="Repair"
              formatter={() => (
                <Button 
                  danger
                  icon={<ReloadOutlined spin={loading['rebuildPositions']} />} 
                  loading={loading['rebuildPositions']}
                  onClick={() => handleAction('rebuildPositions', () => adminApi.rebuildPositions('0', undefined, apiKey))} // 0 represents 'all' or needs specific ID input logic
                >
                  重算所有持倉 (需謹慎)
                </Button>
              )}
            />
            <Text type="secondary" style={{ marginTop: 8, display: 'block' }}>
              重新計算所有使用者的持倉快照 (User Positions)，用於資料修正。
            </Text>
          </Card>
        </Col>

        <Col xs={24} sm={12}>
          <Card>
            <Statistic
              title="估值快照 (Valuation Snapshot)"
              value="Snapshot"
              formatter={() => (
                <Button 
                  icon={<DatabaseOutlined spin={loading['snapshotValuations']} />} 
                  loading={loading['snapshotValuations']}
                  onClick={() => handleAction('snapshotValuations', () => adminApi.snapshotValuations(undefined, undefined, undefined, apiKey))}
                >
                  觸發今日估值快照
                </Button>
              )}
            />
            <Text type="secondary" style={{ marginTop: 8, display: 'block' }}>
              手動觸發所有投資組合的每日估值快照寫入。
            </Text>
          </Card>
        </Col>
      </Row>
    </PageContainer>
  );
};

export default Dashboard;
