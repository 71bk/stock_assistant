import React, { useCallback, useEffect, useState } from 'react';
import { Card, Typography, Row, Col, Statistic, Button, message, Input, InputNumber, Space, Divider, Alert, Popconfirm, Tag } from 'antd';
import { SyncOutlined, ReloadOutlined, DatabaseOutlined, SaveOutlined, KeyOutlined } from '@ant-design/icons';
import { adminApi, type AdminKeyStatus, type SyncResult } from '../../api/admin.api';

const { Title, Text } = Typography;

export const MaintenancePanel: React.FC = () => {
  const [loading, setLoading] = useState<Record<string, boolean>>({});
  const [keyInput, setKeyInput] = useState<string>('');
  const [keyStatus, setKeyStatus] = useState<AdminKeyStatus | null>(null);
  const [savingKey, setSavingKey] = useState(false);
  const [result, setResult] = useState<{
    type: 'success' | 'error';
    message: string;
    details?: string;
  } | null>(null);
  const [rebuildPortfolioId, setRebuildPortfolioId] = useState<number | null>(null);
  const [rebuildInstrumentId, setRebuildInstrumentId] = useState<number | null>(null);

  const refreshKeyStatus = useCallback(async () => {
    try {
      setKeyStatus(await adminApi.getKeyStatus());
    } catch {
      // Status is best-effort; failures here shouldn't block the panel.
      setKeyStatus(null);
    }
  }, []);

  useEffect(() => {
    refreshKeyStatus();
  }, [refreshKeyStatus]);

  const saveApiKey = async () => {
    if (!keyInput.trim()) return;
    setSavingKey(true);
    try {
      const status = await adminApi.setAdminKey(keyInput.trim());
      setKeyStatus(status);
      setKeyInput(''); // Never keep the raw key in component state after it's exchanged for the cookie.
      message.success('Admin Key 已驗證並寫入安全 cookie');
    } catch {
      message.error('Admin Key 驗證失敗，請確認金鑰是否正確');
    } finally {
      setSavingKey(false);
    }
  };

  const clearApiKey = async () => {
    setSavingKey(true);
    try {
      await adminApi.clearAdminKey();
      await refreshKeyStatus();
      message.success('Admin Key 已清除');
    } catch {
      message.error('清除失敗，請稍後再試');
    } finally {
      setSavingKey(false);
    }
  };

  const handleAction = async (action: string, apiCall: () => Promise<unknown>) => {
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
    } catch (err: unknown) {
      console.error(`${action} failed:`, err);
      const errMsg = err instanceof Error ? err.message : '操作失敗';
      setResult({ type: 'error', message: errMsg });
      message.error(errMsg);
    } finally {
      setLoading(prev => ({ ...prev, [action]: false }));
    }
  };

  const keyRequired = keyStatus?.required ?? false;
  const keyActive = keyStatus?.active ?? false;

  return (
    <>
      <Row gutter={[16, 16]}>
        <Col span={24}>
          <Card
            title="Admin Key 驗證"
            variant="borderless"
            extra={
              keyStatus
                ? (keyRequired
                    ? <Tag color={keyActive ? 'success' : 'warning'}>{keyActive ? '已設定' : '未設定'}</Tag>
                    : <Tag color="default">後端未啟用</Tag>)
                : null
            }
          >
            {keyRequired ? (
              <>
                <Space.Compact style={{ width: '100%' }}>
                  <Input.Password
                    prefix={<KeyOutlined />}
                    placeholder="輸入 Admin Key 進行驗證"
                    value={keyInput}
                    onChange={(e) => setKeyInput(e.target.value)}
                    onPressEnter={saveApiKey}
                  />
                  <Button type="primary" icon={<SaveOutlined />} loading={savingKey} onClick={saveApiKey}>
                    驗證並啟用
                  </Button>
                  {keyActive && (
                    <Button danger loading={savingKey} onClick={clearApiKey}>
                      清除
                    </Button>
                  )}
                </Space.Compact>
                <Text type="secondary" style={{ fontSize: 12, display: 'block', marginTop: 8 }}>
                  金鑰驗證後僅寫入 HttpOnly cookie（JavaScript 無法讀取），不再存於瀏覽器 LocalStorage。關閉分頁或逾時後需重新驗證。
                </Text>
              </>
            ) : (
              <Text type="secondary">
                後端未設定 Admin Key，已登入的管理員即可執行下列維護操作，無需額外驗證。
              </Text>
            )}
          </Card>
        </Col>

        {result && (
          <Col span={24}>
            <Alert
              title={result.message}
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
                <Popconfirm
                  title="開始同步股票主檔？"
                  description="此操作會向 Fugle 抓取全部上市櫃股票/ETF 資料，耗時較長。"
                  okText="開始同步"
                  cancelText="取消"
                  onConfirm={() => handleAction('syncInstruments', () => adminApi.syncInstruments())}
                >
                  <Button
                    type="primary"
                    icon={<SyncOutlined spin={loading['syncInstruments']} />}
                    loading={loading['syncInstruments']}
                  >
                    開始同步股票
                  </Button>
                </Popconfirm>
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
                <Popconfirm
                  title="開始同步權證主檔？"
                  description="此操作會向 TPEx + TWSE 抓取全部權證資料與到期日，耗時較長。"
                  okText="開始同步"
                  cancelText="取消"
                  onConfirm={() => handleAction('syncWarrants', () => adminApi.syncWarrants())}
                >
                  <Button
                    type="primary"
                    icon={<SyncOutlined spin={loading['syncWarrants']} />}
                    loading={loading['syncWarrants']}
                  >
                    開始同步權證
                  </Button>
                </Popconfirm>
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
            <Space orientation="vertical" style={{ width: '100%', marginBottom: 12 }}>
              <InputNumber
                style={{ width: '100%' }}
                min={1}
                value={rebuildPortfolioId}
                onChange={(value) => setRebuildPortfolioId(value)}
                placeholder="Portfolio ID（必填）"
              />
              <InputNumber
                style={{ width: '100%' }}
                min={1}
                value={rebuildInstrumentId}
                onChange={(value) => setRebuildInstrumentId(value)}
                placeholder="Instrument ID（可選）"
              />
            </Space>
            <Statistic
              title="持倉重算 (Positions Rebuild)"
              value="Repair"
              formatter={() => (
                <Button
                  danger
                  icon={<ReloadOutlined spin={loading['rebuildPositions']} />}
                  loading={loading['rebuildPositions']}
                  disabled={!rebuildPortfolioId}
                  onClick={() => handleAction(
                    'rebuildPositions',
                    () => adminApi.rebuildPositions(
                      Number(rebuildPortfolioId),
                      rebuildInstrumentId ?? undefined,
                    ),
                  )}
                >
                  重算指定持倉
                </Button>
              )}
            />
            <Text type="secondary" style={{ marginTop: 8, display: 'block' }}>
              依指定 portfolioId（必填）重算持倉；instrumentId 填入可縮小重算範圍。
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
                  onClick={() => handleAction('snapshotValuations', () => adminApi.snapshotValuations())}
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
    </>
  );
};

export default MaintenancePanel;
