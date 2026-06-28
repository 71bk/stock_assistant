import React from 'react';
import { Space, Typography } from 'antd';
import { PageContainer } from '../../../components/layout/PageContainer';
import { RangeFilter } from './RangeFilter';

const { Title } = Typography;

interface AnalyticsPageShellProps {
  title: string;
  /** Hide the date range filter (e.g. for pages without time-bound data). */
  hideRange?: boolean;
  children: React.ReactNode;
}

/** Common shell for admin analytics pages: title + shared range filter + content. */
export const AnalyticsPageShell: React.FC<AnalyticsPageShellProps> = ({ title, hideRange, children }) => (
  <PageContainer>
    <Space orientation="vertical" size={16} style={{ width: '100%' }}>
      <Title level={2} style={{ marginBottom: 0 }}>{title}</Title>
      {!hideRange && <RangeFilter />}
      {children}
    </Space>
  </PageContainer>
);

export default AnalyticsPageShell;
