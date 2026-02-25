import { http } from '../utils/http';
import type { PageData } from '../types/api';

export interface RagChunk {
  content: string;
  documentId: string;
  chunkIndex: number;
  distance: number;
  title?: string;
  sourceType?: string;
  sourceId?: string;
  meta?: Record<string, unknown>;
}

export interface RagDocument {
  id: string;
  title: string;
  sourceType: string;
  sourceId: string;
  meta: Record<string, unknown>;
  createdAt: string;
}

export interface RagQueryResponse {
  chunks: RagChunk[];
}

export interface IngestResponse {
  documentId: string;
  title: string;
  chunksCount: number;
  status: string;
  message: string;
}

export interface IngestTextRequest {
  text: string;
  userId: string;
  title: string;
  sourceType?: string;
  tags?: string;
  sourceId?: string;
}

function normalizeDocument(document: Partial<RagDocument> & { id?: string | number }): RagDocument {
  return {
    id: document.id != null ? String(document.id) : '',
    title: document.title ?? '',
    sourceType: document.sourceType ?? '',
    sourceId: document.sourceId ?? '',
    meta: document.meta ?? {},
    createdAt: document.createdAt ?? '',
  };
}

function normalizeChunk(
  chunk: Partial<RagChunk> & {
    documentId?: string | number;
    chunkIndex?: number;
    distance?: number;
    document_id?: string | number;
    chunk_index?: number;
    score?: number;
    source_type?: string;
    source_id?: string;
  },
): RagChunk {
  const distance =
    typeof chunk.distance === 'number'
      ? chunk.distance
      : typeof chunk.score === 'number'
      ? chunk.score
      : 0;

  return {
    content: chunk.content ?? '',
    documentId: chunk.documentId != null
      ? String(chunk.documentId)
      : chunk.document_id != null
      ? String(chunk.document_id)
      : '',
    chunkIndex: typeof chunk.chunkIndex === 'number'
      ? chunk.chunkIndex
      : typeof chunk.chunk_index === 'number'
      ? chunk.chunk_index
      : 0,
    distance,
    title: chunk.title,
    sourceType: chunk.sourceType ?? chunk.source_type,
    sourceId: chunk.sourceId ?? chunk.source_id,
    meta: chunk.meta,
  };
}

export const ragApi = {
  getDocuments: async (page = 1, size = 20): Promise<PageData<RagDocument>> => {
    const response = await http.get<PageData<RagDocument>>('/rag/documents', { params: { page, size } });
    return {
      ...response,
      items: response.items.map(normalizeDocument),
    };
  },

  deleteDocument: (id: string | number) =>
    http.delete<void>(`/rag/documents/${id}`),

  ingestDocument: async (
    fileId: string,
    title?: string,
    sourceType?: string,
    tags?: string[],
  ): Promise<IngestResponse> => {
    const response = await http.post<IngestResponse>('/rag/documents', {
      fileId,
      title,
      sourceType: sourceType || 'UPLOAD',
      tags,
    });
    return {
      ...response,
      documentId: String(response.documentId),
    };
  },

  ingestText: async (data: IngestTextRequest): Promise<IngestResponse> => {
    const response = await http.post<IngestResponse>('/rag/documents', {
      title: data.title,
      rawText: data.text,
      sourceType: data.sourceType?.toUpperCase() || 'NOTE',
      tags: data.tags,
    });
    return {
      ...response,
      documentId: String(response.documentId),
    };
  },

  query: async (_userId: string, query: string, topK: number = 5, sourceType?: string): Promise<RagQueryResponse> => {
    const response = await http.post<RagQueryResponse>('/rag/query', {
      query,
      topK,
      sourceType: sourceType?.toUpperCase(),
    });
    return {
      chunks: (response.chunks ?? []).map(normalizeChunk),
    };
  },
};
