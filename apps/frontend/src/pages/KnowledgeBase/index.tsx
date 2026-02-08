import React from 'react';
import { Tabs } from 'antd';
import { BookOutlined, ExperimentOutlined } from '@ant-design/icons';
import DocumentManager from './components/DocumentManager';
import RetrievalTester from './components/RetrievalTester';

const KnowledgeBasePage: React.FC = () => {
  const items = [
    {
      key: 'documents',
      label: (
        <span>
          <BookOutlined />
          知識庫管理
        </span>
      ),
      children: <DocumentManager />,
    },
    {
      key: 'test',
      label: (
        <span>
          <ExperimentOutlined />
          檢索測試
        </span>
      ),
      children: <RetrievalTester />,
    },
  ];

  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold mb-6">RAG 知識庫管理</h1>
      <Tabs defaultActiveKey="documents" items={items} />
    </div>
  );
};

export default KnowledgeBasePage;
