import React from 'react';
import { AnalyticsPageShell } from './shared/AnalyticsPageShell';
import { UserAnalyticsSection } from './sections/UserAnalyticsSection';

export const UsersPage: React.FC = () => (
  <AnalyticsPageShell title="使用者分析">
    <UserAnalyticsSection />
  </AnalyticsPageShell>
);

export default UsersPage;
