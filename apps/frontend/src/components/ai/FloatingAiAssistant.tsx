import React, { useState, useRef, useEffect } from 'react';
import { FloatButton, Drawer, Input, Button, List, Avatar, Spin, Space, Typography, message } from 'antd';
import { RobotOutlined, SendOutlined, UserOutlined } from '@ant-design/icons';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { useUIStore } from '../../stores/ui.store';
import { useChatStore } from '../../stores/chat.store';

export const FloatingAiAssistant: React.FC = () => {
  const { chatVisible, setChatVisible } = useUIStore();
  const {
    conversations,
    currentConversationId,
    messages,
    isLoadingList,
    isLoadingConversation,
    isStreaming,
    hasStreamingConflict,
    loadConversations,
    createConversation,
    selectConversation,
    sendMessage,
  } = useChatStore();

  const [inputValue, setInputValue] = useState('');
  const [isListExpanded, setIsListExpanded] = useState(true);
  const scrollRef = useRef<HTMLDivElement>(null);

  // Auto-scroll to bottom when messages change or chat becomes visible
  useEffect(() => {
    if (scrollRef.current && chatVisible) {
      setTimeout(() => {
        if (scrollRef.current) {
          scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
        }
      }, 100); // Small delay to ensure rendering
    }
  }, [messages, chatVisible]);

  useEffect(() => {
    if (chatVisible) {
      loadConversations();
    }
  }, [chatVisible, loadConversations]);

  useEffect(() => {
    if (hasStreamingConflict) {
      message.warning('回覆中請稍候完成，或等回覆結束再切換對話');
    }
  }, [hasStreamingConflict]);

  const handleSend = async () => {
    if (!inputValue.trim() || isStreaming) return;
    const content = inputValue;
    setInputValue('');
    await sendMessage(content);
  };

  return (
    <>
      <FloatButton
        icon={<RobotOutlined />}
        type="primary"
        style={{ right: 24, bottom: 24 }}
        onClick={() => setChatVisible(true)}
        badge={{ dot: isStreaming }}
      />

      <Drawer
        title={
          <Space>
            <RobotOutlined style={{ color: '#1677ff' }} />
            <span>AI 助理</span>
          </Space>
        }
        placement="right"
        onClose={() => setChatVisible(false)}
        open={chatVisible}
        styles={{
          wrapper: { width: 400 },
          body: {
            display: 'flex',
            flexDirection: 'column',
            padding: '0',
          },
        }}
      >
        <div style={{ padding: '12px 16px', borderBottom: '1px solid #f0f0f0', background: '#fff' }}>
          <Space style={{ width: '100%', justifyContent: 'space-between' }}>
            <div
              style={{ cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 8 }}
              onClick={() => setIsListExpanded(!isListExpanded)}
            >
              <Typography.Text strong>對話列表</Typography.Text>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                {isListExpanded ? '收合' : '展開'}
              </Typography.Text>
            </div>
            <Button size="small" onClick={() => createConversation()}>
              新對話
            </Button>
          </Space>
          
          {isListExpanded && (
            <div style={{ maxHeight: 140, overflowY: 'auto', marginTop: 8 }}>
              {isLoadingList ? (
                <Spin size="small" />
              ) : (
                <List
                  size="small"
                  dataSource={conversations}
                  renderItem={(item) => (
                    <List.Item
                      style={{
                        cursor: 'pointer',
                        borderBottom: 'none',
                        padding: '4px 0',
                        color: item.conversationId === currentConversationId ? '#1677ff' : undefined,
                      }}
                      onClick={() => {
                        selectConversation(item.conversationId);
                        // Optional: auto-collapse on selection if desired, but user didn't specify
                      }}
                    >
                      <Typography.Text ellipsis style={{ width: '100%' }}>
                        {item.title || 'New Chat'}
                      </Typography.Text>
                    </List.Item>
                  )}
                />
              )}
            </div>
          )}
        </div>

        <div
          ref={scrollRef}
          style={{
            flex: 1,
            overflowY: 'auto',
            padding: '20px',
            background: '#f9f9f9',
          }}
        >
          {isLoadingConversation ? (
            <div style={{ textAlign: 'center', padding: '40px 0' }}>
              <Spin />
            </div>
          ) : (
            <List
              dataSource={messages}
              renderItem={(item) => (
                <List.Item
                  style={{
                    borderBottom: 'none',
                    padding: '8px 0',
                    justifyContent: item.role === 'user' ? 'flex-end' : 'flex-start',
                  }}
                >
                  <div
                    style={{
                      display: 'flex',
                      flexDirection: item.role === 'user' ? 'row-reverse' : 'row',
                      maxWidth: '85%',
                      gap: '8px',
                    }}
                  >
                    <Avatar
                      icon={item.role === 'user' ? <UserOutlined /> : <RobotOutlined />}
                      style={{
                        backgroundColor: item.role === 'user' ? '#87d068' : '#1677ff',
                        flexShrink: 0,
                      }}
                    />
                    <div
                      style={{
                        padding: '8px 12px',
                        borderRadius: '12px',
                        background: item.role === 'user' ? '#1677ff' : '#fff',
                        color: item.role === 'user' ? '#fff' : '#000',
                        boxShadow: '0 2px 4px rgba(0,0,0,0.05)',
                        whiteSpace: 'normal',
                      }}
                    >
                      {item.role === 'user' ? (
                        <div style={{ whiteSpace: 'pre-wrap' }}>{item.content}</div>
                      ) : (
                        <div className="markdown-content">
                          <ReactMarkdown remarkPlugins={[remarkGfm]}>
                            {item.content}
                          </ReactMarkdown>
                        </div>
                      )}
                    </div>
                  </div>
                </List.Item>
              )}
            />
          )}

          {messages.length === 0 && !isLoadingConversation && (
            <div style={{ textAlign: 'center', marginTop: 100 }}>
              <RobotOutlined style={{ fontSize: 48, color: '#d9d9d9' }} />
              <p style={{ color: '#bfbfbf', marginTop: 16 }}>開始一段新的對話吧</p>
            </div>
          )}
        </div>

        <div style={{ padding: '16px', borderTop: '1px solid #f0f0f0', background: '#fff' }}>
          <Space.Compact style={{ width: '100%' }}>
            <Input
              placeholder="輸入訊息..."
              value={inputValue}
              onChange={(e) => setInputValue(e.target.value)}
              onPressEnter={handleSend}
              disabled={isStreaming}
            />
            {isStreaming ? (
              <Button danger onClick={() => useChatStore.getState().resetChat()}>
                停止
              </Button>
            ) : (
              <Button type="primary" icon={<SendOutlined />} onClick={handleSend} />
            )}
          </Space.Compact>
        </div>
      </Drawer>
    </>
  );
};
