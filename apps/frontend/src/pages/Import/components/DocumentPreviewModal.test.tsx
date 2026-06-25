import { render, screen, waitFor } from '@testing-library/react';
import { vi } from 'vitest';
import { filesApi } from '../../../api/files.api';
import { DocumentPreviewModal } from './DocumentPreviewModal';
import { getPreviewKind } from './documentPreview';

vi.mock('../../../api/files.api', () => ({
  filesApi: {
    getPreview: vi.fn(),
  },
}));

describe('DocumentPreviewModal', () => {
  const createObjectUrl = vi.fn(() => 'blob:preview');
  const revokeObjectUrl = vi.fn();

  beforeEach(() => {
    vi.mocked(filesApi.getPreview).mockReset();
    createObjectUrl.mockClear();
    revokeObjectUrl.mockClear();
    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      value: createObjectUrl,
    });
    Object.defineProperty(URL, 'revokeObjectURL', {
      configurable: true,
      value: revokeObjectUrl,
    });
  });

  it('classifies supported preview content types', () => {
    expect(getPreviewKind('application/pdf')).toBe('pdf');
    expect(getPreviewKind('application/pdf; charset=binary')).toBe('pdf');
    expect(getPreviewKind('image/jpeg')).toBe('image');
    expect(getPreviewKind('text/plain')).toBeNull();
  });

  it('renders a PDF blob in an iframe and releases the URL', async () => {
    vi.mocked(filesApi.getPreview).mockResolvedValue({
      blob: new Blob(['pdf'], { type: 'application/pdf' }),
      contentType: 'application/pdf',
    });

    const { unmount } = render(
      <DocumentPreviewModal fileId="11" open onClose={vi.fn()} />,
    );

    const iframe = await screen.findByTestId('pdf-preview');
    expect(iframe).toHaveAttribute('src', 'blob:preview');
    expect(createObjectUrl).toHaveBeenCalledTimes(1);

    unmount();
    expect(revokeObjectUrl).toHaveBeenCalledWith('blob:preview');
  });

  it('renders an image blob', async () => {
    vi.mocked(filesApi.getPreview).mockResolvedValue({
      blob: new Blob(['image'], { type: 'image/jpeg' }),
      contentType: 'image/jpeg',
    });

    render(<DocumentPreviewModal fileId="12" open onClose={vi.fn()} />);

    expect(await screen.findByAltText('原始文件')).toHaveAttribute('src', 'blob:preview');
  });

  it('shows an error for unsupported content types', async () => {
    vi.mocked(filesApi.getPreview).mockResolvedValue({
      blob: new Blob(['text'], { type: 'text/plain' }),
      contentType: 'text/plain',
    });

    render(<DocumentPreviewModal fileId="13" open onClose={vi.fn()} />);

    expect(await screen.findByText('無法預覽文件')).toBeInTheDocument();
    expect(screen.getByText('不支援預覽此檔案格式：text/plain')).toBeInTheDocument();
    await waitFor(() => expect(createObjectUrl).not.toHaveBeenCalled());
  });
});
