import { http } from '../utils/http';

export interface FileDownloadUrlResponse {
  url: string;
  expiresAt?: string;
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
