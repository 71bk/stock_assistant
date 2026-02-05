import { create } from 'zustand';
import { message as uiMessage } from 'antd';
import { chatApi } from '../api/chat.api';
import type { ConversationMessage, ConversationSummary } from '../api/chat.api';
import { fetchSseWithRetry } from '../utils/sse';

interface ChatState {
  conversations: ConversationSummary[];
  currentConversationId: string | null;
  messages: ConversationMessage[];
  isLoadingList: boolean;
  isLoadingConversation: boolean;
  isStreaming: boolean;
  streamingConversationId: string | null;
  hasStreamingConflict: boolean;

  loadConversations: () => Promise<void>;
  createConversation: () => Promise<string | null>;
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

let currentChatAbortController: AbortController | null = null;

export const useChatStore = create<ChatState>((set, get) => ({
  conversations: [],
  currentConversationId: null,
  messages: [],
  isLoadingList: false,
  isLoadingConversation: false,
  isStreaming: false,
  streamingConversationId: null,
  hasStreamingConflict: false,

  resetChat: () => set({ messages: [], currentConversationId: null }),

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

    if (currentChatAbortController) {
      currentChatAbortController.abort();
    }
    currentChatAbortController = new AbortController();

    let conversationId = get().currentConversationId;
    if (!conversationId) {
      conversationId = await get().createConversation();
      if (!conversationId) return;
    }

    const clientMessageId = newClientMessageId();
    const tempUserId = `tmp-user-${clientMessageId}`;
    const tempAssistantId = `tmp-assistant-${clientMessageId}`;

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
      await fetchSseWithRetry({
        url: `/api/ai/conversations/${conversationId}/messages`,
        body: { content: trimmed, clientMessageId },
        signal: currentChatAbortController.signal,
        onDelta: (text) => {
          set((state) => ({
            messages: state.messages.map((msg) =>
              msg.messageId === tempAssistantId ? { ...msg, content: (msg.content || '') + text } : msg
            ),
          }));
        },
        onDone: (payload) => {
          set((state) => ({
            isStreaming: false,
            streamingConversationId: null,
            messages: state.messages.map((msg) =>
              msg.messageId === tempAssistantId && payload?.assistantMessageId
                ? { ...msg, messageId: payload.assistantMessageId as string }
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
      currentChatAbortController = null;
    }
  },
}));
