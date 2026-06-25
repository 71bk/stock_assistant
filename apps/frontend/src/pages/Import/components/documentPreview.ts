export type PreviewKind = 'image' | 'pdf';

export function getPreviewKind(contentType: string): PreviewKind | null {
  const normalized = contentType.split(';', 1)[0].trim().toLowerCase();
  if (normalized === 'application/pdf') {
    return 'pdf';
  }
  if (normalized.startsWith('image/')) {
    return 'image';
  }
  return null;
}
