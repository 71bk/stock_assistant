import React from 'react';
import { Card, Statistic, theme } from 'antd';

interface StatCardProps {
  title: React.ReactNode;
  value?: number | string;
  precision?: number;
  suffix?: string;
  prefix?: React.ReactNode;
  /**
   * Color the value once it exceeds 0 — used for error rates / failure counts
   * so anomalies stand out instead of looking identical to healthy zeros.
   */
  alertWhenPositive?: 'danger' | 'warning';
  loading?: boolean;
}

/**
 * A single KPI card. Cards stretch to equal height within a Row so wrapping
 * titles (e.g. on mobile) don't create a ragged grid.
 */
export const StatCard: React.FC<StatCardProps> = ({
  title,
  value,
  precision,
  suffix,
  prefix,
  alertWhenPositive,
  loading,
}) => {
  const { token } = theme.useToken();

  const numeric = typeof value === 'number' ? value : Number(value);
  const isAlert = alertWhenPositive != null && Number.isFinite(numeric) && numeric > 0;
  const valueColor = isAlert
    ? (alertWhenPositive === 'danger' ? token.colorError : token.colorWarning)
    : undefined;

  return (
    <Card style={{ height: '100%' }} styles={{ body: { padding: 20 } }} loading={loading}>
      <Statistic
        title={title}
        value={value}
        precision={precision}
        suffix={suffix}
        prefix={prefix}
        valueStyle={valueColor ? { color: valueColor } : undefined}
      />
    </Card>
  );
};

export default StatCard;
