import React, { useMemo } from 'react';
import { Alert, Card, Col, Row, Space, Table } from 'antd';
import { Area } from '@ant-design/plots';
import dayjs from 'dayjs';
import { analyticsApi } from '../../../api/analytics.api';
import { useUIStore } from '../../../stores/ui.store';
import { useAnalyticsResource } from '../shared/useAnalyticsData';
import { StatCard } from '../shared/StatCard';
import { ChartCard } from '../shared/ChartCard';

export const ApiTrafficSection: React.FC = () => {
  const theme = useUIStore((s) => s.theme);
  const chartTheme = theme === 'dark' ? 'classicDark' : 'classic';

  const { data, loading, error } = useAnalyticsResource(analyticsApi.getApiTraffic);

  const trendData = useMemo(
    () => (data?.trend ?? []).map((item) => ({
      time: dayjs(item.time).format('MM-DD HH:mm'),
      value: item.requestCount,
    })),
    [data],
  );

  return (
    <Space orientation="vertical" size={16} style={{ width: '100%' }}>
      {error && <Alert type="error" showIcon title={error} />}

      <Row gutter={[16, 16]}>
        <Col xs={12} sm={8} lg={6}><StatCard title="API 請求" value={data?.requestCount ?? 0} loading={loading} /></Col>
        <Col xs={12} sm={8} lg={6}><StatCard title="API 錯誤率" value={data?.errorRatePercent ?? 0} precision={2} suffix="%" alertWhenPositive="warning" loading={loading} /></Col>
        <Col xs={12} sm={8} lg={6}><StatCard title="API 4xx" value={data?.clientErrorCount ?? 0} alertWhenPositive="warning" loading={loading} /></Col>
        <Col xs={12} sm={8} lg={6}><StatCard title="API 5xx" value={data?.serverErrorCount ?? 0} alertWhenPositive="danger" loading={loading} /></Col>
        <Col xs={12} sm={8} lg={6}><StatCard title="API p95 延遲" value={data?.p95LatencyMs ?? 0} precision={0} suffix="ms" loading={loading} /></Col>
      </Row>

      {data && !data.available && (
        <Alert
          type="warning"
          showIcon
          title="API 流量資料目前不可用"
          description={data.warning || 'Prometheus 尚未設定或暫時無法查詢'}
        />
      )}

      <Card title="熱門 API">
        <Table
          rowKey={(row) => `${row.method}:${row.uri}`}
          size="small"
          pagination={false}
          loading={loading}
          dataSource={data?.topEndpoints ?? []}
          columns={[
            { title: 'Method', dataIndex: 'method', width: 90 },
            { title: 'URI Template', dataIndex: 'uri' },
            { title: '請求數', dataIndex: 'requestCount', width: 90 },
          ]}
        />
      </Card>

      {data?.available && (
        <ChartCard title="API 請求趨勢" isEmpty={trendData.length === 0} height={280}>
          <Area
            data={trendData}
            xField="time"
            yField="value"
            theme={chartTheme}
            axis={{ y: { min: 0 } }}
          />
        </ChartCard>
      )}
    </Space>
  );
};

export default ApiTrafficSection;
