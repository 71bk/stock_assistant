import React from 'react';
import { Typography } from 'antd';
import { PageContainer } from '../../components/layout/PageContainer';
import { MaintenancePanel } from './MaintenancePanel';

const { Title } = Typography;

export const MaintenancePage: React.FC = () => (
  <PageContainer>
    <Title level={2}>維護工具</Title>
    <MaintenancePanel />
  </PageContainer>
);

export default MaintenancePage;
