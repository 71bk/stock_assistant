import React from 'react';
import { Alert, Card, Col, Row, Space, Typography } from 'antd';
import { BarChartOutlined, RobotOutlined, ApiOutlined, RightOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { analyticsApi } from '../../../api/analytics.api';
import { useAnalyticsResource } from '../shared/useAnalyticsData';
import { StatCard } from '../shared/StatCard';

const { Text } = Typography;

interface QuickLink {
  to: string;
  icon: React.ReactNode;
  title: string;
  desc: string;
}

const quickLinks: QuickLink[] = [
  { to: '/admin/users', icon: <BarChartOutlined />, title: '使用者分析', desc: '註冊 / 活躍 / 趨勢 / 熱門頁面' },
  { to: '/admin/ai-usage', icon: <RobotOutlined />, title: 'AI 用量', desc: 'Tokens / 呼叫 / OCR / 模型明細' },
  { to: '/admin/api-traffic', icon: <ApiOutlined />, title: 'API 流量', desc: '請求 / 錯誤率 / 延遲 / 熱門 API' },
];

export const OverviewSection: React.FC = () => {
  const navigate = useNavigate();

  const summary = useAnalyticsResource(analyticsApi.getSummary);
  const api = useAnalyticsResource(analyticsApi.getApiTraffic);
  const ai = useAnalyticsResource(analyticsApi.getAiUsage);

  const s = summary.data;
  const a = api.data;
  const u = ai.data;
  const error = summary.error || api.error || ai.error;
  const aiTokens = u ? u.inputTokens + u.outputTokens + u.cachedTokens : 0;

  return (
    <Space orientation="vertical" size={16} style={{ width: '100%' }}>
      {error && <Alert type="error" showIcon title={error} />}

      <Row gutter={[16, 16]}>
        <Col xs={12} sm={8} lg={6}><StatCard title="總註冊使用者" value={s?.totalUsers ?? 0} loading={summary.loading} /></Col>
        <Col xs={12} sm={8} lg={6}><StatCard title="期間新增" value={s?.newUsers ?? 0} loading={summary.loading} /></Col>
        <Col xs={12} sm={8} lg={6}><StatCard title="DAU" value={s?.dau ?? 0} loading={summary.loading} /></Col>
        <Col xs={12} sm={8} lg={6}><StatCard title="MAU" value={s?.mau ?? 0} loading={summary.loading} /></Col>
        <Col xs={12} sm={8} lg={6}><StatCard title="API 請求" value={a?.requestCount ?? 0} loading={api.loading} /></Col>
        <Col xs={12} sm={8} lg={6}><StatCard title="API 錯誤率" value={a?.errorRatePercent ?? 0} precision={2} suffix="%" alertWhenPositive="warning" loading={api.loading} /></Col>
        <Col xs={12} sm={8} lg={6}><StatCard title="AI Tokens 總計" value={aiTokens} loading={ai.loading} /></Col>
        <Col xs={12} sm={8} lg={6}><StatCard title="AI 呼叫失敗率" value={u?.failureRatePercent ?? 0} precision={2} suffix="%" alertWhenPositive="danger" loading={ai.loading} /></Col>
      </Row>

      <Row gutter={[16, 16]}>
        {quickLinks.map((link) => (
          <Col xs={24} sm={8} key={link.to}>
            <Card
              hoverable
              styles={{ body: { display: 'flex', alignItems: 'center', gap: 12 } }}
              onClick={() => navigate(link.to)}
            >
              <span style={{ fontSize: 22 }}>{link.icon}</span>
              <span style={{ flex: 1 }}>
                <div style={{ fontWeight: 600 }}>{link.title}</div>
                <Text type="secondary" style={{ fontSize: 12 }}>{link.desc}</Text>
              </span>
              <RightOutlined style={{ opacity: 0.45 }} />
            </Card>
          </Col>
        ))}
      </Row>
    </Space>
  );
};

export default OverviewSection;
