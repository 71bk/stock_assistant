import React from 'react';
import { Tabs, Typography } from 'antd';
import { PageContainer } from '../../components/layout/PageContainer';
import { AnalyticsPanel } from './AnalyticsPanel';
import { MaintenancePanel } from './MaintenancePanel';

const { Title } = Typography;

export const Dashboard: React.FC = () => (
  <PageContainer>
    <Title level={2}>管理後台</Title>
    <Tabs
      defaultActiveKey="analytics"
      items={[
        {
          key: 'analytics',
          label: '網站分析',
          children: <AnalyticsPanel />,
        },
        {
          key: 'maintenance',
          label: '維護工具',
          children: <MaintenancePanel />,
        },
      ]}
    />
  </PageContainer>
);

export default Dashboard;
