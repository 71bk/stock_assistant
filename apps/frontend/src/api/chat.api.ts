import { http } from '../utils/http';

export type ChatRole = 'system' | 'user' | 'assistant';

export interface ConversationSummary {
  conversationId: string;
  title?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ConversationMessage {
  messageId: string;
  role: ChatRole;
  content: string;
  status?: string | null;
  createdAt?: string;
}

export interface ConversationDetail {
  conversationId: string;
  title?: string;
  summary?: string | null;
  createdAt?: string;
  updatedAt?: string;
  messages: ConversationMessage[];
}

export const chatApi = {
  createConversation: (title?: string) =>
    http.post<ConversationSummary>('/ai/conversations', { title }),

  listConversations: () =>
    http.get<ConversationSummary[]>('/ai/conversations'),

  getConversation: (conversationId: string, limit?: number) =>
    http.get<ConversationDetail>(`/ai/conversations/${conversationId}`, { params: { limit } }),

  updateConversation: (conversationId: string, title: string) =>
    http.patch<ConversationSummary>(`/ai/conversations/${conversationId}`, { title }),

  deleteConversation: (conversationId: string) =>
    http.delete<void>(`/ai/conversations/${conversationId}`),

  sendMessage: (conversationId: string, content: string, clientMessageId?: string) =>
    http.post(`/ai/conversations/${conversationId}/messages`, { content, clientMessageId }),
};
