import React, { useMemo } from 'react';
import { Alert, Card, Col, Row, Space, Table } from 'antd';
import { Area, Line } from '@ant-design/plots';
import { analyticsApi } from '../../../api/analytics.api';
import { useUIStore } from '../../../stores/ui.store';
import { useAnalyticsResource } from '../shared/useAnalyticsData';
import { StatCard } from '../shared/StatCard';
import { ChartCard } from '../shared/ChartCard';

export const UserAnalyticsSection: React.FC = () => {
  const theme = useUIStore((s) => s.theme);
  const chartTheme = theme === 'dark' ? 'classicDark' : 'classic';

  const summary = useAnalyticsResource(analyticsApi.getSummary);
  const trend = useAnalyticsResource(analyticsApi.getUserTrend);
  const pages = useAnalyticsResource(analyticsApi.getTopPages);

  const trendRows = trend.data ?? [];
  const pageRows = pages.data ?? [];

  const userTrendData = useMemo(
    () => trendRows.flatMap((item) => [
      { date: item.date, metric: '新增使用者', value: item.newUsers },
      { date: item.date, metric: '活躍使用者', value: item.activeUsers },
    ]),
    [trendRows],
  );

  const pageViewData = useMemo(
    () => trendRows.map((item) => ({ date: item.date, value: item.pageViews })),
    [trendRows],
  );

  const s = summary.data;
  const error = summary.error || trend.error || pages.error;

  return (
    <Space orientation="vertical" size={16} style={{ width: '100%' }}>
      {error && <Alert type="error" showIcon title={error} />}

      <Row gutter={[16, 16]}>
        <Col xs={12} sm={8} lg={6}><StatCard title="總註冊使用者" value={s?.totalUsers ?? 0} loading={summary.loading} /></Col>
        <Col xs={12} sm={8} lg={6}><StatCard title="期間新增" value={s?.newUsers ?? 0} loading={summary.loading} /></Col>
        <Col xs={12} sm={8} lg={6}><StatCard title="DAU" value={s?.dau ?? 0} loading={summary.loading} /></Col>
        <Col xs={12} sm={8} lg={6}><StatCard title="WAU" value={s?.wau ?? 0} loading={summary.loading} /></Col>
        <Col xs={12} sm={8} lg={6}><StatCard title="MAU" value={s?.mau ?? 0} loading={summary.loading} /></Col>
        <Col xs={12} sm={8} lg={6}><StatCard title="頁面瀏覽" value={s?.pageViews ?? 0} loading={summary.loading} /></Col>
        <Col xs={12} sm={8} lg={6}><StatCard title="工作階段" value={s?.sessions ?? 0} loading={summary.loading} /></Col>
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={12}>
          <ChartCard title="使用者趨勢" isEmpty={userTrendData.length === 0}>
            <Line
              data={userTrendData}
              xField="date"
              yField="value"
              colorField="metric"
              theme={chartTheme}
              axis={{ y: { min: 0 } }}
            />
          </ChartCard>
        </Col>
        <Col xs={24} xl={12}>
          <ChartCard title="頁面瀏覽趨勢" isEmpty={pageViewData.length === 0}>
            <Area
              data={pageViewData}
              xField="date"
              yField="value"
              theme={chartTheme}
              axis={{ y: { min: 0 } }}
            />
          </ChartCard>
        </Col>
      </Row>

      <Card title="熱門頁面">
        <Table
          rowKey="route"
          size="small"
          pagination={false}
          loading={pages.loading}
          dataSource={pageRows}
          columns={[
            { title: '路由', dataIndex: 'route' },
            { title: '瀏覽', dataIndex: 'views', width: 90 },
            { title: '使用者', dataIndex: 'uniqueUsers', width: 90 },
            { title: '工作階段', dataIndex: 'sessions', width: 100 },
          ]}
        />
      </Card>
    </Space>
  );
};

export default UserAnalyticsSection;
