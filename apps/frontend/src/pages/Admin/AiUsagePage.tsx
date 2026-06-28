import React from 'react';
import { AnalyticsPageShell } from './shared/AnalyticsPageShell';
import { AiUsageSection } from './sections/AiUsageSection';

export const AiUsagePage: React.FC = () => (
  <AnalyticsPageShell title="AI 用量">
    <AiUsageSection />
  </AnalyticsPageShell>
);

export default AiUsagePage;
