import React, { useState } from 'react';
import { Card, Input, Button, List, Tag, Typography, Slider, Space, Empty, Spin } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import { ragApi, type RagChunk } from '@/api/rag.api';
import { useAuthStore } from '@/stores/auth.store';

const { Paragraph, Text } = Typography;

const RetrievalTester: React.FC = () => {
  const [query, setQuery] = useState('');
  const { user } = useAuthStore();
  const [topK, setTopK] = useState(5);
  const [loading, setLoading] = useState(false);
  const [results, setResults] = useState<RagChunk[]>([]);
  const [searched, setSearched] = useState(false);

  const handleSearch = async () => {
    if (!query.trim()) return;
    
    setLoading(true);
    setSearched(true);
    try {
      const userId = user?.id || '1';
      const response = await ragApi.query(userId, query, topK);
      setResults(response.chunks || []);
    } catch (error) {
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="space-y-6">
      <Card title="RAG 檢索測試">
        <Space direction="vertical" style={{ width: '100%' }} size="large">
          <div>
            <Text strong>查詢語句 (Query)</Text>
            <div className="flex gap-2 mt-2">
              <Input 
                size="large" 
                placeholder="輸入問題或關鍵字來測試檢索效果..." 
                value={query}
                onChange={e => setQuery(e.target.value)}
                onPressEnter={handleSearch}
              />
              <Button 
                type="primary" 
                size="large" 
                icon={<SearchOutlined />} 
                onClick={handleSearch}
                loading={loading}
              >
                檢索
              </Button>
            </div>
          </div>

          <div>
            <div className="flex justify-between">
              <Text>檢索數量 (Top K): {topK}</Text>
            </div>
            <Slider 
              min={1} 
              max={20} 
              value={topK} 
              onChange={setTopK} 
            />
          </div>
        </Space>
      </Card>

      {searched && (
        <Card title={`檢索結果 (${results.length})`}>
          <Spin spinning={loading}>
            {results.length > 0 ? (
              <List
                itemLayout="vertical"
                dataSource={results}
                renderItem={(item) => (
                  <List.Item
                    key={`${item.document_id}-${item.chunk_index}`}
                    style={{ padding: '16px', borderBottom: '1px solid #f0f0f0' }}
                  >
                    <List.Item.Meta
                      title={
                        <div className="flex justify-between items-center">
                          <Space>
                            <Text strong>{item.title || 'Untitled'}</Text>
                            <Tag color="blue">{item.source_type}</Tag>
                            {item.document_id && <Tag>{`Doc ID: ${item.document_id}`}</Tag>}
                          </Space>
                          <Tag color={item.score > 0.7 ? 'green' : 'orange'}>
                            相似度: {(item.score * 100).toFixed(1)}%
                          </Tag>
                        </div>
                      }
                      description={
                        <div className="mt-2">
                          <Paragraph 
                            ellipsis={{ rows: 3, expandable: true, symbol: '展開' }}
                            className="bg-gray-50 p-3 rounded"
                          >
                            {item.content}
                          </Paragraph>
                        </div>
                      }
                    />
                  </List.Item>
                )}
              />
            ) : (
              <Empty description="未找到相關內容" />
            )}
          </Spin>
        </Card>
      )}
    </div>
  );
};

export default RetrievalTester;
