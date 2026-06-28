import React from 'react';
import { AnalyticsPageShell } from './shared/AnalyticsPageShell';
import { OverviewSection } from './sections/OverviewSection';

export const OverviewPage: React.FC = () => (
  <AnalyticsPageShell title="總覽">
    <OverviewSection />
  </AnalyticsPageShell>
);

export default OverviewPage;
