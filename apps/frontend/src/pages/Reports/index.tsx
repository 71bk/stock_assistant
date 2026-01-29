import React from 'react';
import { Typography, Card, Empty } from 'antd';

const { Title } = Typography;

const Reports: React.FC = () => {
  return (
    <div>
      <Title level={2}>Analysis Reports</Title>
      <Card>
        <Empty description="No reports generated yet" />
      </Card>
    </div>
  );
};

export default Reports;
