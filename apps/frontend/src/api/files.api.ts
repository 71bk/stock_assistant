import { http } from '../utils/http';

export interface FileDownloadUrlResponse {
  url: string;
  expiresAt?: string;
}

export interface FileMetadataResponse {
  fileId: string;
  sha256: string;
  sizeBytes: number;
  contentType: string;
  createdAt: string;
}

export interface FileUploadResponse {
  fileId: string;
  sha256: string;
  sizeBytes: number;
  contentType: string;
}

export const filesApi = {
  getDownloadUrl: (fileId: string) =>
    http.get<FileDownloadUrlResponse>(`/files/${fileId}/url`),

  getMetadata: (fileId: string) =>
    http.get<FileMetadataResponse>(`/files/${fileId}`),

  getContent: (fileId: string) =>
    http.get<Blob>(`/files/${fileId}/content`, {
      responseType: 'blob',
    }),

  getPreview: async (fileId: string) => {
    const [metadata, blob] = await Promise.all([
      filesApi.getMetadata(fileId),
      filesApi.getContent(fileId),
    ]);

    return {
      blob,
      contentType: metadata.contentType || blob.type,
    };
  },

  uploadFile: (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    return http.post<FileUploadResponse>('/files', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
  },
};
