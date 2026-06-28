import React from 'react';
import { Card, Empty } from 'antd';

interface ChartCardProps {
  title: React.ReactNode;
  /** When true, render a compact empty state instead of a full-height blank chart. */
  isEmpty: boolean;
  emptyText?: string;
  /** Chart height when data is present. */
  height?: number;
  size?: 'default' | 'small';
  extra?: React.ReactNode;
  children: React.ReactNode;
}

/**
 * Card wrapper for charts. Avoids the "tall empty box" problem by collapsing to
 * a short empty state when there is no data (common before metrics are wired up).
 */
export const ChartCard: React.FC<ChartCardProps> = ({
  title,
  isEmpty,
  emptyText = '尚無資料',
  height = 300,
  size = 'default',
  extra,
  children,
}) => (
  <Card title={title} size={size} extra={extra}>
    {isEmpty ? (
      <Empty
        image={Empty.PRESENTED_IMAGE_SIMPLE}
        description={emptyText}
        style={{ margin: '16px 0' }}
      />
    ) : (
      <div style={{ height }}>{children}</div>
    )}
  </Card>
);

export default ChartCard;
