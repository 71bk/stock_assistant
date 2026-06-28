import React, { useEffect } from 'react';
import { Select } from 'antd';
import { usePortfolioStore } from '../../stores/portfolio.store';

/**
 * Global active-portfolio switcher shown in the app header. Lets users with more
 * than one portfolio switch context; hidden when they have none. Pages re-fetch
 * via their effects on currentPortfolioId.
 */
export const PortfolioSwitcher: React.FC = () => {
  const portfolios = usePortfolioStore((s) => s.portfolios);
  const currentPortfolioId = usePortfolioStore((s) => s.currentPortfolioId);
  const setCurrentPortfolio = usePortfolioStore((s) => s.setCurrentPortfolio);
  const initPortfolioId = usePortfolioStore((s) => s.initPortfolioId);

  // Ensure the list is loaded even on pages that don't fetch portfolio data.
  useEffect(() => {
    initPortfolioId();
  }, [initPortfolioId]);

  if (!portfolios || portfolios.length === 0) {
    return null;
  }

  return (
    <Select
      value={currentPortfolioId ?? undefined}
      onChange={(value) => setCurrentPortfolio(value)}
      options={portfolios.map((p) => ({ value: String(p.id), label: p.name }))}
      style={{ minWidth: 150, maxWidth: 220 }}
      aria-label="切換投資組合"
    />
  );
};

export default PortfolioSwitcher;
