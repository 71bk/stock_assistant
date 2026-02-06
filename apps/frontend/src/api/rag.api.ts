import { http } from '../utils/http';

export interface RagChunk {
  content: string;
  document_id: string;
  chunk_index: number;
  distance: number;
  title: string;
  source_type: string;
  source_id: string;
  meta: Record<string, unknown>;
}

export interface RagQueryResponse {
  chunks: RagChunk[];
}

export const ragApi = {
  createDocument: (data: { rawText?: string; fileId?: string }) =>
    http.post<{ documentId: string }>('/rag/documents', data),

  query: (data: { query: string }) =>
    http.post<RagQueryResponse>('/rag/query', data),
};
