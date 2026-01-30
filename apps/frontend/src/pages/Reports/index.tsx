import React from 'react';
import { Typography, Card, Empty } from 'antd';

const { Title } = Typography;

const Reports: React.FC = () => {
  return (
    <div>
      <Title level={2}>分析報告</Title>
      <Card>
        <Empty description="尚未產生報告" />
      </Card>
    </div>
  );
};

export default Reports;
