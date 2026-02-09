import { http } from '../utils/http';

export interface FileDownloadUrlResponse {
  url: string;
  expiresAt?: string;
}

export const filesApi = {
  getDownloadUrl: (fileId: string) =>
    http.get<FileDownloadUrlResponse>(`/files/${fileId}/url`),
    
  // Helper to construct the preview element based on content type
  // This is not an API call but a helper, or we can handle it in the component
};
