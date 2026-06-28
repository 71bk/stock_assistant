import React, { useMemo } from 'react';
import { Alert, Card, Col, Row, Space, Table } from 'antd';
import { Line, Pie } from '@ant-design/plots';
import dayjs from 'dayjs';
import { analyticsApi } from '../../../api/analytics.api';
import { useUIStore } from '../../../stores/ui.store';
import { useAnalyticsResource } from '../shared/useAnalyticsData';
import { StatCard } from '../shared/StatCard';
import { ChartCard } from '../shared/ChartCard';

export const AiUsageSection: React.FC = () => {
  const theme = useUIStore((s) => s.theme);
  const chartTheme = theme === 'dark' ? 'classicDark' : 'classic';

  const { data, loading, error } = useAnalyticsResource(analyticsApi.getAiUsage);

  const providerTokenData = useMemo(
    () => (data?.byProvider ?? [])
      .map((item) => ({ name: item.key, value: item.inputTokens + item.outputTokens + item.cachedTokens }))
      .filter((item) => item.value > 0),
    [data],
  );
  const operationTokenData = useMemo(
    () => (data?.byOperation ?? [])
      .map((item) => ({ name: item.key, value: item.inputTokens + item.outputTokens + item.cachedTokens }))
      .filter((item) => item.value > 0),
    [data],
  );
  const tokenTrendData = useMemo(
    () => (data?.trend ?? []).flatMap((item) => [
      { time: dayjs(item.time).format('MM-DD HH:mm'), type: 'Input', value: item.inputTokens },
      { time: dayjs(item.time).format('MM-DD HH:mm'), type: 'Output', value: item.outputTokens },
      { time: dayjs(item.time).format('MM-DD HH:mm'), type: 'Cached', value: item.cachedTokens },
    ]),
    [data],
  );

  return (
    <Space orientation="vertical" size={16} style={{ width: '100%' }}>
      {error && <Alert type="error" showIcon title={error} />}
      {data && !data.available && (
        <Alert
          type="warning"
          showIcon
          title="AI 用量資料目前不可用"
          description={data.warning || 'Prometheus 尚未收到 AI metrics'}
        />
      )}

      <Row gutter={[16, 16]}>
        <Col xs={12} sm={8} lg={6}><StatCard title="Input Tokens" value={data?.inputTokens ?? 0} loading={loading} /></Col>
        <Col xs={12} sm={8} lg={6}><StatCard title="Output Tokens" value={data?.outputTokens ?? 0} loading={loading} /></Col>
        <Col xs={12} sm={8} lg={6}><StatCard title="Cached Tokens" value={data?.cachedTokens ?? 0} loading={loading} /></Col>
        <Col xs={12} sm={8} lg={6}><StatCard title="供應商呼叫" value={data?.callCount ?? 0} loading={loading} /></Col>
        <Col xs={12} sm={8} lg={6}><StatCard title="呼叫失敗率" value={data?.failureRatePercent ?? 0} precision={2} suffix="%" alertWhenPositive="danger" loading={loading} /></Col>
        <Col xs={12} sm={8} lg={6}><StatCard title="AI p95 延遲" value={data?.p95DurationMs ?? 0} precision={0} suffix="ms" loading={loading} /></Col>
        <Col xs={12} sm={8} lg={6}><StatCard title="OCR Fallback" value={data?.ocrFallbackCount ?? 0} alertWhenPositive="warning" loading={loading} /></Col>
        <Col xs={12} sm={8} lg={6}><StatCard title="OCR Retry/Reparse" value={data?.ocrRetryCount ?? 0} alertWhenPositive="warning" loading={loading} /></Col>
        <Col xs={12} sm={8} lg={6}><StatCard title="Embedding Retry" value={data?.embeddingRetryCount ?? 0} alertWhenPositive="warning" loading={loading} /></Col>
        <Col xs={12} sm={8} lg={6}><StatCard title="Embedding Batch Fallback" value={data?.embeddingBatchFallbackCount ?? 0} alertWhenPositive="warning" loading={loading} /></Col>
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={12}>
          <ChartCard title="Provider Token 分布" size="small" isEmpty={providerTokenData.length === 0} emptyText="尚無 Token 資料" height={280}>
            <Pie data={providerTokenData} angleField="value" colorField="name" theme={chartTheme} label={{ text: 'name', position: 'outside' }} />
          </ChartCard>
        </Col>
        <Col xs={24} xl={12}>
          <ChartCard title="Operation Token 分布" size="small" isEmpty={operationTokenData.length === 0} emptyText="尚無 Token 資料" height={280}>
            <Pie data={operationTokenData} angleField="value" colorField="name" theme={chartTheme} label={{ text: 'name', position: 'outside' }} />
          </ChartCard>
        </Col>
      </Row>

      <ChartCard title="Token 趨勢" size="small" isEmpty={tokenTrendData.length === 0} height={280}>
        <Line data={tokenTrendData} xField="time" yField="value" colorField="type" theme={chartTheme} axis={{ y: { min: 0 } }} />
      </ChartCard>

      <Card size="small" title="模型用量明細">
        <Table
          rowKey={(row) => `${row.provider}:${row.model}:${row.operation}`}
          size="small"
          pagination={false}
          loading={loading}
          dataSource={data?.models ?? []}
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
      </Card>
    </Space>
  );
};

export default AiUsageSection;
