import React from 'react';
import { AnalyticsPageShell } from './shared/AnalyticsPageShell';
import { ApiTrafficSection } from './sections/ApiTrafficSection';

export const ApiTrafficPage: React.FC = () => (
  <AnalyticsPageShell title="API 流量">
    <ApiTrafficSection />
  </AnalyticsPageShell>
);

export default ApiTrafficPage;
