import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Card,
  Col,
  DatePicker,
  Empty,
  Row,
  Skeleton,
  Space,
  Statistic,
  Table,
  Typography,
} from 'antd';
import { Area, Line, Pie } from '@ant-design/plots';
import dayjs, { type Dayjs } from 'dayjs';
import {
  analyticsApi,
  type AiUsage,
  type AnalyticsDailyTrend,
  type AnalyticsPageMetric,
  type AnalyticsSummary,
  type ApiTraffic,
} from '../../api/analytics.api';
import { useUIStore } from '../../stores/ui.store';

const { Text } = Typography;
const { RangePicker } = DatePicker;

type DateRange = [Dayjs, Dayjs];

const defaultRange = (): DateRange => [dayjs().subtract(6, 'day'), dayjs()];

export const AnalyticsPanel: React.FC = () => {
  const { theme } = useUIStore();
  const [range, setRange] = useState<DateRange>(defaultRange);
  const [summary, setSummary] = useState<AnalyticsSummary | null>(null);
  const [trend, setTrend] = useState<AnalyticsDailyTrend[]>([]);
  const [pages, setPages] = useState<AnalyticsPageMetric[]>([]);
  const [apiTraffic, setApiTraffic] = useState<ApiTraffic | null>(null);
  const [aiUsage, setAiUsage] = useState<AiUsage | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    const query = {
      from: range[0].format('YYYY-MM-DD'),
      to: range[1].format('YYYY-MM-DD'),
      timezone: 'Asia/Taipei',
    };
    try {
      const [summaryData, trendData, pageData, apiData, aiUsageData] = await Promise.all([
        analyticsApi.getSummary(query),
        analyticsApi.getUserTrend(query),
        analyticsApi.getTopPages(query),
        analyticsApi.getApiTraffic(query),
        analyticsApi.getAiUsage(query),
      ]);
      setSummary(summaryData);
      setTrend(trendData);
      setPages(pageData);
      setApiTraffic(apiData);
      setAiUsage(aiUsageData);
    } catch (err) {
      setError(err instanceof Error ? err.message : '分析資料載入失敗');
    } finally {
      setLoading(false);
    }
  }, [range]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const userTrendData = useMemo(
    () => trend.flatMap((item) => [
      { date: item.date, metric: '新增使用者', value: item.newUsers },
      { date: item.date, metric: '活躍使用者', value: item.activeUsers },
    ]),
    [trend],
  );

  const pageViewData = useMemo(
    () => trend.map((item) => ({ date: item.date, value: item.pageViews })),
    [trend],
  );

  const apiTrendData = useMemo(
    () => (apiTraffic?.trend || []).map((item) => ({
      time: dayjs(item.time).format('MM-DD HH:mm'),
      value: item.requestCount,
    })),
    [apiTraffic],
  );

  const chartTheme = theme === 'dark' ? 'classicDark' : 'classic';
  const providerTokenData = useMemo(
    () => (aiUsage?.byProvider || []).map((item) => ({
      name: item.key,
      value: item.inputTokens + item.outputTokens + item.cachedTokens,
    })).filter((item) => item.value > 0),
    [aiUsage],
  );
  const operationTokenData = useMemo(
    () => (aiUsage?.byOperation || []).map((item) => ({
      name: item.key,
      value: item.inputTokens + item.outputTokens + item.cachedTokens,
    })).filter((item) => item.value > 0),
    [aiUsage],
  );
  const aiTokenTrendData = useMemo(
    () => (aiUsage?.trend || []).flatMap((item) => [
      { time: dayjs(item.time).format('MM-DD HH:mm'), type: 'Input', value: item.inputTokens },
      { time: dayjs(item.time).format('MM-DD HH:mm'), type: 'Output', value: item.outputTokens },
      { time: dayjs(item.time).format('MM-DD HH:mm'), type: 'Cached', value: item.cachedTokens },
    ]),
    [aiUsage],
  );

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Space wrap>
        <Text strong>統計期間</Text>
        <RangePicker
          value={range}
          allowClear={false}
          presets={[
            { label: '今日', value: [dayjs(), dayjs()] },
            { label: '本月', value: [dayjs().startOf('month'), dayjs()] },
            { label: '最近 7 天', value: [dayjs().subtract(6, 'day'), dayjs()] },
            { label: '最近 30 天', value: [dayjs().subtract(29, 'day'), dayjs()] },
          ]}
          disabledDate={(current) =>
            current.isAfter(dayjs(), 'day')
            || current.isBefore(dayjs().subtract(89, 'day'), 'day')
          }
          onChange={(value) => {
            if (value?.[0] && value?.[1]) {
              setRange([value[0], value[1]]);
            }
          }}
        />
      </Space>

      {error && <Alert type="error" showIcon message={error} />}

      {loading && !summary ? (
        <Skeleton active paragraph={{ rows: 10 }} />
      ) : (
        <>
          <Row gutter={[16, 16]}>
            <Col xs={12} md={6}><Card><Statistic title="總註冊使用者" value={summary?.totalUsers || 0} /></Card></Col>
            <Col xs={12} md={6}><Card><Statistic title="期間新增" value={summary?.newUsers || 0} /></Card></Col>
            <Col xs={12} md={4}><Card><Statistic title="DAU" value={summary?.dau || 0} /></Card></Col>
            <Col xs={12} md={4}><Card><Statistic title="WAU" value={summary?.wau || 0} /></Card></Col>
            <Col xs={12} md={4}><Card><Statistic title="MAU" value={summary?.mau || 0} /></Card></Col>
            <Col xs={12} md={6}><Card><Statistic title="頁面瀏覽" value={summary?.pageViews || 0} /></Card></Col>
            <Col xs={12} md={6}><Card><Statistic title="工作階段" value={summary?.sessions || 0} /></Card></Col>
            <Col xs={12} md={6}><Card><Statistic title="API 請求" value={apiTraffic?.requestCount || 0} /></Card></Col>
            <Col xs={12} md={6}><Card><Statistic title="API 錯誤率" value={apiTraffic?.errorRatePercent || 0} precision={2} suffix="%" /></Card></Col>
            <Col xs={12} md={6}><Card><Statistic title="API 4xx" value={apiTraffic?.clientErrorCount || 0} /></Card></Col>
            <Col xs={12} md={6}><Card><Statistic title="API 5xx" value={apiTraffic?.serverErrorCount || 0} /></Card></Col>
            <Col xs={12} md={6}><Card><Statistic title="API p95 延遲" value={apiTraffic?.p95LatencyMs || 0} precision={0} suffix="ms" /></Card></Col>
          </Row>

          {apiTraffic && !apiTraffic.available && (
            <Alert
              type="warning"
              showIcon
              message="API 流量資料目前不可用"
              description={apiTraffic.warning || 'Prometheus 尚未設定或暫時無法查詢'}
            />
          )}

          <Card title="AI 與 OCR 用量">
            <Space direction="vertical" size={16} style={{ width: '100%' }}>
              {aiUsage && !aiUsage.available && (
                <Alert
                  type="warning"
                  showIcon
                  message="AI 用量資料目前不可用"
                  description={aiUsage.warning || 'Prometheus 尚未收到 AI metrics'}
                />
              )}
              <Row gutter={[16, 16]}>
                <Col xs={12} md={6}><Statistic title="Input Tokens" value={aiUsage?.inputTokens || 0} /></Col>
                <Col xs={12} md={6}><Statistic title="Output Tokens" value={aiUsage?.outputTokens || 0} /></Col>
                <Col xs={12} md={6}><Statistic title="Cached Tokens" value={aiUsage?.cachedTokens || 0} /></Col>
                <Col xs={12} md={6}><Statistic title="供應商呼叫" value={aiUsage?.callCount || 0} /></Col>
                <Col xs={12} md={6}><Statistic title="呼叫失敗率" value={aiUsage?.failureRatePercent || 0} precision={2} suffix="%" /></Col>
                <Col xs={12} md={6}><Statistic title="AI p95 延遲" value={aiUsage?.p95DurationMs || 0} precision={0} suffix="ms" /></Col>
                <Col xs={12} md={6}><Statistic title="OCR Fallback" value={aiUsage?.ocrFallbackCount || 0} /></Col>
                <Col xs={12} md={6}><Statistic title="OCR Retry/Reparse" value={aiUsage?.ocrRetryCount || 0} /></Col>
                <Col xs={12} md={6}><Statistic title="Embedding Retry" value={aiUsage?.embeddingRetryCount || 0} /></Col>
                <Col xs={12} md={6}><Statistic title="Embedding Batch Fallback" value={aiUsage?.embeddingBatchFallbackCount || 0} /></Col>
              </Row>

              <Row gutter={[16, 16]}>
                <Col xs={24} xl={12}>
                  <Card size="small" title="Provider Token 分布">
                    <div style={{ height: 280 }}>
                      {providerTokenData.length > 0 ? (
                        <Pie
                          data={providerTokenData}
                          angleField="value"
                          colorField="name"
                          theme={chartTheme}
                          label={{ text: 'name', position: 'outside' }}
                        />
                      ) : <Empty description="尚無 Token 資料" />}
                    </div>
                  </Card>
                </Col>
                <Col xs={24} xl={12}>
                  <Card size="small" title="Operation Token 分布">
                    <div style={{ height: 280 }}>
                      {operationTokenData.length > 0 ? (
                        <Pie
                          data={operationTokenData}
                          angleField="value"
                          colorField="name"
                          theme={chartTheme}
                          label={{ text: 'name', position: 'outside' }}
                        />
                      ) : <Empty description="尚無 Token 資料" />}
                    </div>
                  </Card>
                </Col>
              </Row>

              <Card size="small" title="Token 趨勢">
                <div style={{ height: 280 }}>
                  <Line
                    data={aiTokenTrendData}
                    xField="time"
                    yField="value"
                    colorField="type"
                    theme={chartTheme}
                    axis={{ y: { min: 0 } }}
                  />
                </div>
              </Card>

              <Table
                rowKey={(row) => `${row.provider}:${row.model}:${row.operation}`}
                size="small"
                pagination={false}
                dataSource={aiUsage?.models || []}
                columns={[
                  { title: 'Provider', dataIndex: 'provider' },
                  { title: 'Model', dataIndex: 'model' },
                  { title: 'Operation', dataIndex: 'operation' },
                  { title: 'Input', dataIndex: 'inputTokens' },
                  { title: 'Output', dataIndex: 'outputTokens' },
                  { title: 'Cached', dataIndex: 'cachedTokens' },
                  { title: 'Calls', dataIndex: 'callCount' },
                  { title: 'Failed', dataIndex: 'failedCallCount' },
                ]}
              />
            </Space>
          </Card>

          <Row gutter={[16, 16]}>
            <Col xs={24} xl={12}>
              <Card title="使用者趨勢">
                <div style={{ height: 300 }}>
                  <Line
                    data={userTrendData}
                    xField="date"
                    yField="value"
                    colorField="metric"
                    theme={chartTheme}
                    axis={{ y: { min: 0 } }}
                  />
                </div>
              </Card>
            </Col>
            <Col xs={24} xl={12}>
              <Card title="頁面瀏覽趨勢">
                <div style={{ height: 300 }}>
                  <Area
                    data={pageViewData}
                    xField="date"
                    yField="value"
                    theme={chartTheme}
                    axis={{ y: { min: 0 } }}
                  />
                </div>
              </Card>
            </Col>
          </Row>

          <Row gutter={[16, 16]}>
            <Col xs={24} xl={12}>
              <Card title="熱門頁面">
                <Table
                  rowKey="route"
                  size="small"
                  pagination={false}
                  dataSource={pages}
                  columns={[
                    { title: '路由', dataIndex: 'route' },
                    { title: '瀏覽', dataIndex: 'views', width: 90 },
                    { title: '使用者', dataIndex: 'uniqueUsers', width: 90 },
                    { title: '工作階段', dataIndex: 'sessions', width: 100 },
                  ]}
                />
              </Card>
            </Col>
            <Col xs={24} xl={12}>
              <Card title="熱門 API">
                <Table
                  rowKey={(row) => `${row.method}:${row.uri}`}
                  size="small"
                  pagination={false}
                  dataSource={apiTraffic?.topEndpoints || []}
                  columns={[
                    { title: 'Method', dataIndex: 'method', width: 90 },
                    { title: 'URI Template', dataIndex: 'uri' },
                    { title: '請求數', dataIndex: 'requestCount', width: 90 },
                  ]}
                />
              </Card>
            </Col>
          </Row>

          {apiTraffic?.available && (
            <Card title="API 請求趨勢">
              <div style={{ height: 280 }}>
                <Area
                  data={apiTrendData}
                  xField="time"
                  yField="value"
                  theme={chartTheme}
                  axis={{ y: { min: 0 } }}
                />
              </div>
            </Card>
          )}
        </>
      )}
    </Space>
  );
};

export default AnalyticsPanel;
