import { http } from '../utils/http';

export interface RagChunk {
  content: string;
  document_id: number;
  chunk_index: number;
  score: number;
  title?: string;
  source_type?: string;
  source_id?: string;
}

export interface RagQueryResponse {
  chunks: RagChunk[];
}

export interface IngestResponse {
  document_id: string;
  title: string;
  chunks_count: number;
  status: string;
  message: string;
}

export interface IngestTextRequest {
  text: string;
  user_id: string;
  title: string;
  source_type?: string;
  tags?: string; // Comma separated
  source_id?: string;
}

export const ragApi = {
  ingestDocument: (file: File, userId: string, title?: string, tags?: string) => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('user_id', userId);
    formData.append('source_type', 'upload');
    if (title) formData.append('title', title);
    if (tags) formData.append('tags', tags);

    // Note: We use a custom axios instance or just use 'http' but with the new base URL.
    // Since 'http' instance has baseURL set to '/api', we need to override it or use a relative path that goes out of /api.
    // But standard axios baseURL is usually prepended.
    // If http has baseURL='/api', then calling '/rag-api/ingest' results in '/api/rag-api/ingest' which is wrong.
    // 使用 Java Backend Proxy (/api/rag/documents)
    return http.post<IngestResponse>('/rag/documents', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
  },

  ingestText: (data: IngestTextRequest) => {
    // 使用 Java Backend Proxy，這裡假設後端有對應的純文字處理 endpoint，
    // 或統一使用 /rag/documents 處理 (需視後端實作而定，這裡先對齊 Java API 合約 v1)
    // 依據合約: POST /api/rag/documents 建立文件
    // 如果後端支援 rawText，通常是 JSON body 或 multipart 包含 text 欄位
    
    // 這裡暫時維持 multipart 格式傳送給後端
    const formData = new FormData();
    formData.append('content', data.text); // 注意: 後端可能預期 'content' 或 'rawText'
    formData.append('title', data.title);
    formData.append('sourceType', data.source_type || 'NOTE'); // Enum 大寫
    
    // 注意：v1 合約中 POST /api/rag/documents
    // 若後端接收 JSON: { title, content, sourceType }
    return http.post<IngestResponse>('/rag/documents', {
      title: data.title,
      content: data.text,
      sourceType: data.source_type?.toUpperCase() || 'NOTE',
      tags: data.tags
    });
  },

  query: (userId: string, query: string, topK: number = 5, sourceType?: string) => {
    // 使用 Java Backend Proxy (/api/rag/query)
    return http.post<RagQueryResponse>('/rag/query', {
      // user_id 通常由後端從 Session/Token 取，不需要前端傳
      query,
      topK,
      sourceType: sourceType?.toUpperCase(),
    });
  },
};
