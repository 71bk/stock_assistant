import React, { useEffect, useState } from 'react';
import { Alert, Button, Empty, Image, Modal, Spin } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { filesApi } from '../../../api/files.api';
import { logger } from '../../../utils/logger';
import { getPreviewKind, type PreviewKind } from './documentPreview';

interface DocumentPreviewModalProps {
  fileId: string | null;
  open: boolean;
  onClose: () => void;
}

export const DocumentPreviewModal: React.FC<DocumentPreviewModalProps> = ({
  fileId,
  open,
  onClose,
}) => {
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [previewKind, setPreviewKind] = useState<PreviewKind | null>(null);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    if (!open || !fileId) {
      setPreviewUrl(null);
      setPreviewKind(null);
      setLoading(false);
      setErrorMessage(null);
      return;
    }

    let disposed = false;
    let objectUrl: string | null = null;

    setPreviewUrl(null);
    setPreviewKind(null);
    setLoading(true);
    setErrorMessage(null);

    const loadPreview = async () => {
      try {
        const preview = await filesApi.getPreview(fileId);
        const contentType = preview.contentType || preview.blob.type;
        const kind = getPreviewKind(contentType);

        if (!kind) {
          if (!disposed) {
            setErrorMessage(`不支援預覽此檔案格式：${contentType || '未知格式'}`);
          }
          return;
        }

        objectUrl = URL.createObjectURL(preview.blob);
        if (disposed) {
          URL.revokeObjectURL(objectUrl);
          objectUrl = null;
          return;
        }

        setPreviewKind(kind);
        setPreviewUrl(objectUrl);
      } catch (error) {
        logger.error('Failed to load document preview', error);
        if (!disposed) {
          setErrorMessage('文件載入失敗，請稍後再試。');
        }
      } finally {
        if (!disposed) {
          setLoading(false);
        }
      }
    };

    void loadPreview();

    return () => {
      disposed = true;
      if (objectUrl) {
        URL.revokeObjectURL(objectUrl);
      }
    };
  }, [fileId, open, reloadKey]);

  return (
    <Modal
      title="原始文件預覽"
      open={open}
      onCancel={onClose}
      footer={null}
      width={960}
      destroyOnHidden
      styles={{
        body: {
          height: '70vh',
          minHeight: 480,
          maxHeight: 760,
          overflow: 'auto',
          padding: 0,
          background: '#f5f5f5',
        },
      }}
    >
      <div
        style={{
          width: '100%',
          height: '100%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        {loading ? (
          <div style={{ textAlign: 'center' }}>
            <Spin />
            <div style={{ marginTop: 12 }}>載入預覽中...</div>
          </div>
        ) : errorMessage ? (
          <Alert
            type="error"
            showIcon
            title="無法預覽文件"
            description={errorMessage}
            action={
              <Button
                size="small"
                icon={<ReloadOutlined />}
                onClick={() => setReloadKey((value) => value + 1)}
              >
                重新載入
              </Button>
            }
          />
        ) : previewUrl && previewKind === 'pdf' ? (
          <iframe
            src={previewUrl}
            title="PDF 文件預覽"
            data-testid="pdf-preview"
            style={{ width: '100%', height: '100%', border: 0 }}
          />
        ) : previewUrl && previewKind === 'image' ? (
          <Image
            src={previewUrl}
            alt="原始文件"
            style={{ maxWidth: '100%', maxHeight: '68vh', objectFit: 'contain' }}
          />
        ) : (
          <Empty description="沒有可預覽的文件" />
        )}
      </div>
    </Modal>
  );
};
