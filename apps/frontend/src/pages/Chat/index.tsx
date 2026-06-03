import React, { useEffect, useRef, useState } from 'react';
import { Layout, Menu, Input, Button, Avatar, Typography, Spin, Empty, Dropdown } from 'antd';
import type { MenuProps } from 'antd';
import {
  SendOutlined,
  PlusOutlined,
  MessageOutlined,
  RobotOutlined,
  UserOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  DeleteOutlined,
  MoreOutlined,
  EditOutlined,
} from '@ant-design/icons';
import { useChatStore } from '../../stores/chat.store';
import { useAuthStore } from '../../stores/auth.store';
import { preprocessMarkdown } from '../../utils/format';
import { AiReportViewer } from '../../components/ai/AiReportViewer';
import dayjs from 'dayjs';

const { Sider, Content } = Layout;
const { Title, Text } = Typography;
const { TextArea } = Input;

const ChatPage: React.FC = () => {
  const {
    conversations,
    currentConversationId,
    messages,
    isLoadingList,
    isLoadingConversation,
    isStreaming,
    loadConversations,
    createConversation,
    updateConversationTitle,
    deleteConversation,
    selectConversation,
    sendMessage,
  } = useChatStore();
  const { user } = useAuthStore();
  const [inputValue, setInputValue] = useState('');
  const [siderCollapsed, setSiderCollapsed] = useState(false);
  const [hoveredConversationId, setHoveredConversationId] = useState<string | null>(null);
  const [editingConversationId, setEditingConversationId] = useState<string | null>(null);
  const [editTitleValue, setEditTitleValue] = useState('');
  const scrollRef = useRef<HTMLDivElement>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    loadConversations();
  }, [loadConversations]);

  const scrollToBottom = () => {
    if (scrollRef.current) {
      scrollRef.current.scrollTo({ top: scrollRef.current.scrollHeight, behavior: 'smooth' });
    }
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages, isStreaming, isLoadingConversation]);

  const handleSend = async () => {
    if (!inputValue.trim() || isStreaming) return;
    const content = inputValue;
    setInputValue('');
    await sendMessage(content);
  };

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <Layout style={{ height: 'calc(100vh - 160px)', background: '#fff', borderRadius: 8, overflow: 'hidden' }}>
      <Sider
        width={260}
        theme="light"
        collapsible
        collapsed={siderCollapsed}
        onCollapse={setSiderCollapsed}
        trigger={null}
        style={{ borderRight: '1px solid #f0f0f0' }}
      >
        <div style={{ padding: siderCollapsed ? '16px 0' : 16, display: 'flex', justifyContent: 'center' }}>
          {siderCollapsed ? (
            <Button
              type="primary"
              shape="circle"
              icon={<PlusOutlined />}
              onClick={() => createConversation()}
              disabled={isStreaming}
            />
          ) : (
            <Button
              type="dashed"
              block
              icon={<PlusOutlined />}
              onClick={() => createConversation()}
              disabled={isStreaming}
            >
              新對話
            </Button>
          )}
        </div>
        <div style={{ height: 'calc(100% - 72px)', overflowY: 'auto', overflowX: 'hidden' }}>
          {isLoadingList ? (
            <div style={{ textAlign: 'center', padding: 20 }}><Spin size="small" /></div>
          ) : (
            <Menu
              mode="inline"
              inlineCollapsed={siderCollapsed}
              selectedKeys={currentConversationId ? [currentConversationId] : []}
              onClick={({ key, domEvent }) => {
                if ((domEvent.target as HTMLElement).closest('.chat-menu-actions') || (domEvent.target as HTMLElement).closest('.ant-dropdown')) {
                  return;
                }
                selectConversation(key);
              }}
              items={conversations.map((c) => {
                const isHovered = hoveredConversationId === c.conversationId;
                const isEditing = editingConversationId === c.conversationId;

                const menuItems: MenuProps['items'] = [
                  {
                    key: 'rename',
                    icon: <EditOutlined />,
                    label: '重新命名',
                    onClick: (e) => {
                      e.domEvent.stopPropagation();
                      setEditingConversationId(c.conversationId);
                      setEditTitleValue(c.title || '');
                    }
                  },
                  {
                    key: 'delete',
                    icon: <DeleteOutlined />,
                    label: '刪除',
                    danger: true,
                    onClick: (e) => {
                      e.domEvent.stopPropagation();
                      deleteConversation(c.conversationId);
                    }
                  }
                ];

                return {
                  key: c.conversationId,
                  icon: <MessageOutlined />,
                  onMouseEnter: () => setHoveredConversationId(c.conversationId),
                  onMouseLeave: () => setHoveredConversationId(null),
                  label: siderCollapsed ? null : (
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', height: '100%' }}>
                      {isEditing ? (
                        <Input
                          size="small"
                          value={editTitleValue}
                          autoFocus
                          onChange={(e) => setEditTitleValue(e.target.value)}
                          onPressEnter={async (e) => {
                            e.stopPropagation();
                            await updateConversationTitle(c.conversationId, editTitleValue);
                            setEditingConversationId(null);
                          }}
                          onBlur={async () => {
                            if (editTitleValue !== (c.title || '')) {
                              await updateConversationTitle(c.conversationId, editTitleValue);
                            }
                            setEditingConversationId(null);
                          }}
                          onClick={(e) => e.stopPropagation()}
                          style={{ flex: 1, marginRight: 8 }}
                        />
                      ) : (
                        <Typography.Text style={{ color: 'inherit', flex: 1 }} ellipsis>
                          {c.title || '新對話'}
                        </Typography.Text>
                      )}
                      
                      {!isEditing && isHovered && (
                        <div className="chat-menu-actions" onClick={e => e.stopPropagation()}>
                          <Dropdown menu={{ items: menuItems }} trigger={['click']}>
                            <Button
                              type="text"
                              size="small"
                              icon={<MoreOutlined />}
                              style={{ color: 'inherit', flexShrink: 0 }}
                            />
                          </Dropdown>
                        </div>
                      )}
                    </div>
                  ),
                };
              })}
            />
          )}
        </div>
      </Sider>

      <Content style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
        {/* Header */}
        <div style={{ padding: '12px 24px', borderBottom: '1px solid #f0f0f0', display: 'flex', alignItems: 'center', gap: 16 }}>
          <Button
            type="text"
            icon={siderCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setSiderCollapsed(!siderCollapsed)}
            style={{ fontSize: 16 }}
          />
          <div>
            <Title level={4} style={{ margin: 0 }}>
              {conversations.find(c => c.conversationId === currentConversationId)?.title || 'AI 助理'}
            </Title>
            <Text type="secondary" style={{ fontSize: 12 }}>
              基於 RAG 與即時行情數據的投資顧問
            </Text>
          </div>
        </div>

        {/* Messages */}
        <div 
          ref={scrollRef}
          style={{ flex: 1, overflowY: 'auto', padding: '24px 0', background: '#f9f9f9' }}
        >
          <div style={{ maxWidth: 800, margin: '0 auto', padding: '0 24px' }}>
            {isLoadingConversation ? (
              <div style={{ textAlign: 'center', marginTop: 40 }}>
                <Spin size="large" />
                <div style={{ marginTop: 12, color: '#8c8c8c' }}>載入對話中...</div>
              </div>
            ) : messages.length === 0 ? (
              <Empty
                image={<RobotOutlined style={{ fontSize: 48, color: '#1677ff' }} />}
                description={
                  <div style={{ marginTop: 16 }}>
                    <Title level={4}>我是您的智慧投資助理</Title>
                    <Text type="secondary">
                      您可以詢問我關於股票行情、財報分析、投資組合建議或是任何金融知識。
                    </Text>
                  </div>
                }
                style={{ marginTop: 60 }}
              />
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column' }}>
                {messages.map((msg) => (
                  <div
                    key={msg.messageId}
                    style={{
                      marginBottom: 24,
                      display: 'flex',
                      gap: 16,
                      flexDirection: msg.role === 'user' ? 'row-reverse' : 'row'
                    }}
                  >
                    <Avatar
                      icon={msg.role === 'user' ? <UserOutlined /> : <RobotOutlined />}
                      src={msg.role === 'user' ? user?.pictureUrl : undefined}
                      style={{
                        backgroundColor: msg.role === 'user' ? '#1677ff' : '#87d068',
                        flexShrink: 0
                      }}
                    />
                    <div style={{
                      maxWidth: '85%',
                      backgroundColor: msg.role === 'user' ? '#1677ff' : '#fff',
                      color: msg.role === 'user' ? '#fff' : 'inherit',
                      padding: '12px 16px',
                      borderRadius: 12,
                      boxShadow: '0 2px 8px rgba(0,0,0,0.05)',
                    }}>
                      <div
                        className={msg.role === 'assistant' ? "assistant-content" : ""}
                        style={msg.role === 'assistant' ? { fontSize: '16px', lineHeight: '1.8' } : {}}
                      >
                        {msg.role === 'assistant' ? (
                          <AiReportViewer
                            content={preprocessMarkdown(msg.content || (isStreaming && msg.messageId.startsWith('tmp-') ? '...' : ''))}
                          />
                        ) : (
                          <div style={{ whiteSpace: 'pre-wrap' }}>{msg.content}</div>
                        )}
                      </div>
                      <div style={{
                        marginTop: 4,
                        fontSize: 10,
                        textAlign: msg.role === 'user' ? 'right' : 'left',
                        opacity: 0.6
                      }}>
                        {msg.createdAt ? dayjs(msg.createdAt).format('HH:mm') : dayjs().format('HH:mm')}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
            {isStreaming && (
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, color: '#1677ff', fontSize: 12, marginLeft: 56 }}>
                <Spin size="small" /> AI 正在生成中...
              </div>
            )}
            <div ref={messagesEndRef} />
          </div>
        </div>

        {/* Input */}
        <div style={{ padding: '24px', borderTop: '1px solid #f0f0f0' }}>
          <div style={{ maxWidth: 800, margin: '0 auto', position: 'relative' }}>
            <TextArea
              value={inputValue}
              onChange={(e) => setInputValue(e.target.value)}
              onKeyDown={handleKeyPress}
              placeholder="輸入訊息... (Shift + Enter 換行)"
              autoSize={{ minRows: 2, maxRows: 6 }}
              style={{ paddingRight: 50, borderRadius: 12 }}
              disabled={isStreaming}
            />
            <Button
              type="primary"
              shape="circle"
              icon={<SendOutlined />}
              onClick={handleSend}
              disabled={!inputValue.trim() || isStreaming}
              style={{ position: 'absolute', right: 10, bottom: 10 }}
            />
          </div>
          <div style={{ textAlign: 'center', marginTop: 8, fontSize: 12, color: '#8c8c8c' }}>
            AI 生成內容僅供參考，不構成投資建議。
          </div>
        </div>
      </Content>
    </Layout>
  );
};

export default ChatPage;
