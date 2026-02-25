import { create } from 'zustand';
import { message as uiMessage } from 'antd';
import { chatApi } from '../api/chat.api';
import type { ConversationMessage, ConversationSummary } from '../api/chat.api';
import { fetchSseWithRetry } from '../utils/sse';
import { env } from '../app/env';

interface ChatState {
  conversations: ConversationSummary[];
  currentConversationId: string | null;
  messages: ConversationMessage[];
  isLoadingList: boolean;
  isLoadingConversation: boolean;
  isStreaming: boolean;
  streamingConversationId: string | null;
  hasStreamingConflict: boolean;
  abortController: AbortController | null;

  loadConversations: () => Promise<void>;
  createConversation: () => Promise<string | null>;
  updateConversationTitle: (conversationId: string, title: string) => Promise<void>;
  selectConversation: (conversationId: string) => Promise<void>;
  sendMessage: (content: string) => Promise<void>;
  resetChat: () => void;
}

const newClientMessageId = () => {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID();
  }
  return `msg-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
};

const toUiMessages = (messages: ConversationMessage[]) =>
  messages.filter((m) => m.role !== 'system');

export const useChatStore = create<ChatState>((set, get) => ({
  conversations: [],
  currentConversationId: null,
  messages: [],
  isLoadingList: false,
  isLoadingConversation: false,
  isStreaming: false,
  streamingConversationId: null,
  hasStreamingConflict: false,
  abortController: null,

  resetChat: () => {
    // Abort any ongoing stream when resetting
    const { abortController } = get();
    if (abortController) {
      abortController.abort();
    }
    set({ messages: [], currentConversationId: null, abortController: null });
  },

  loadConversations: async () => {
    set({ isLoadingList: true });
    try {
      const data = await chatApi.listConversations();
      set({ conversations: data });
      if (!get().currentConversationId && data.length > 0) {
        await get().selectConversation(data[0].conversationId);
      }
    } catch (e) {
      console.error('Failed to load conversations', e);
    } finally {
      set({ isLoadingList: false });
    }
  },

  createConversation: async () => {
    try {
      const conversation = await chatApi.createConversation();
      set((state) => ({
        conversations: [conversation, ...state.conversations],
        currentConversationId: conversation.conversationId,
        messages: [],
      }));
      return conversation.conversationId;
    } catch (e) {
      console.error('Failed to create conversation', e);
      uiMessage.error('建立對話失敗');
      return null;
    }
  },

  updateConversationTitle: async (conversationId: string, title: string) => {
    try {
      const updated = await chatApi.updateConversation(conversationId, title);
      set((state) => ({
        conversations: state.conversations.map((c) =>
          c.conversationId === conversationId ? updated : c
        ),
      }));
      uiMessage.success('標題已更新');
    } catch (e) {
      console.error('Failed to update title', e);
      uiMessage.error('更新標題失敗');
    }
  },

  selectConversation: async (conversationId: string) => {
    if (get().isStreaming && get().streamingConversationId !== conversationId) {
      set({ hasStreamingConflict: true });
      return;
    }
    set({ isLoadingConversation: true });
    try {
      const detail = await chatApi.getConversation(conversationId, 50);
      set({
        currentConversationId: conversationId,
        messages: toUiMessages(detail.messages),
      });
    } catch (e) {
      console.error('Failed to load conversation', e);
      uiMessage.error('載入對話失敗');
    } finally {
      set({ isLoadingConversation: false });
    }
  },

  sendMessage: async (content: string) => {
    if (get().isStreaming) return;
    const trimmed = content.trim();
    if (!trimmed) return;

    // Abort previous controller if exists
    const prevController = get().abortController;
    if (prevController) {
      prevController.abort();
    }

    const abortController = new AbortController();
    set({ abortController });

    let conversationId = get().currentConversationId;
    if (!conversationId) {
      conversationId = await get().createConversation();
      if (!conversationId) return;
    }

    const clientMessageId = newClientMessageId();
    const tempUserId = `tmp-user-${clientMessageId}`;
    const tempAssistantId = `tmp-assistant-${clientMessageId}`;
    let currentUserMessageId = tempUserId;
    let currentAssistantMessageId = tempAssistantId;

    set((state) => ({
      isStreaming: true,
      streamingConversationId: conversationId,
      hasStreamingConflict: false,
      messages: [
        ...state.messages,
        { messageId: tempUserId, role: 'user', content: trimmed },
        { messageId: tempAssistantId, role: 'assistant', content: '' },
      ],
    }));

    try {
      const apiBaseUrl = env.API_BASE_URL.endsWith('/') ? env.API_BASE_URL.slice(0, -1) : env.API_BASE_URL;
      await fetchSseWithRetry({
        url: `${apiBaseUrl}/ai/conversations/${conversationId}/messages`,
        body: { content: trimmed, clientMessageId },
        signal: abortController.signal,
        onMeta: (payload) => {
          const userMessageId = typeof payload?.userMessageId === 'string'
            ? payload.userMessageId
            : null;
          const assistantMessageId = typeof payload?.assistantMessageId === 'string'
            ? payload.assistantMessageId
            : null;
          if (!userMessageId && !assistantMessageId) {
            return;
          }
          const previousUserMessageId = currentUserMessageId;
          const previousAssistantMessageId = currentAssistantMessageId;
          if (userMessageId) {
            currentUserMessageId = userMessageId;
          }
          if (assistantMessageId) {
            currentAssistantMessageId = assistantMessageId;
          }
          set((state) => ({
            messages: state.messages.map((msg) => {
              if (userMessageId && msg.messageId === previousUserMessageId) {
                return { ...msg, messageId: userMessageId };
              }
              if (assistantMessageId && msg.messageId === previousAssistantMessageId) {
                return { ...msg, messageId: assistantMessageId };
              }
              return msg;
            }),
          }));
        },
        onDelta: (text) => {
          const targetAssistantMessageId = currentAssistantMessageId;
          set((state) => ({
            messages: state.messages.map((msg) =>
              msg.messageId === targetAssistantMessageId ? { ...msg, content: (msg.content || '') + text } : msg
            ),
          }));
        },
        onDone: (payload) => {
          const targetAssistantMessageId = currentAssistantMessageId;
          const nextAssistantMessageId = typeof payload?.assistantMessageId === 'string'
            ? payload.assistantMessageId
            : null;
          if (nextAssistantMessageId) {
            currentAssistantMessageId = nextAssistantMessageId;
          }
          set((state) => ({
            isStreaming: false,
            streamingConversationId: null,
            messages: state.messages.map((msg) =>
              msg.messageId === targetAssistantMessageId && nextAssistantMessageId
                ? { ...msg, messageId: nextAssistantMessageId }
                : msg
            ),
          }));
        },
        onError: (err) => {
          uiMessage.error(err.message || 'AI 回覆失敗');
        }
      });

      set({ isStreaming: false, streamingConversationId: null });
      await get().loadConversations();
    } catch (e) {
      console.error('Chat SSE error', e);
      if ((e as Error).name !== 'AbortError') {
        uiMessage.error('AI 回覆失敗');
      }
      set({ isStreaming: false, streamingConversationId: null });
    } finally {
      set({ abortController: null });
    }
  },
}));
