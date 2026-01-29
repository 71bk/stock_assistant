import React from 'react';
import { Typography, Card, Empty } from 'antd';

const { Title } = Typography;

const Trades: React.FC = () => {
  return (
    <div>
      <Title level={2}>Trade History</Title>
      <Card>
        <Empty description="No trades found" />
      </Card>
    </div>
  );
};

export default Trades;
