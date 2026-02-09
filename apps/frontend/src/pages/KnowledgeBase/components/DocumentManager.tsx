import React, { useState, useEffect } from 'react';
import { Card, Form, Upload, Button, Input, message, Tabs, Alert, Table, Tag, Popconfirm } from 'antd';
import { InboxOutlined, FileTextOutlined, DeleteOutlined, ReloadOutlined, DatabaseOutlined } from '@ant-design/icons';
import { ragApi } from '@/api/rag.api';
import type { RagDocument } from '@/api/rag.api';
import type { UploadFile } from 'antd/es/upload/interface';
import { useAuthStore } from '@/stores/auth.store';
import { formatDateTime } from '@/utils/format';
import { logger } from '@/utils/logger';

const { Dragger } = Upload;
const { TextArea } = Input;

const DocumentManager: React.FC = () => {
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [uploading, setUploading] = useState(false);
  const [documents, setDocuments] = useState<RagDocument[]>([]);
  const [loadingDocs, setLoadingDocs] = useState(false);
  const [totalDocs, setTotalDocs] = useState(0);
  const [textForm] = Form.useForm();
  const { user } = useAuthStore();

  const fetchDocuments = async (page = 1, size = 10) => {
    setLoadingDocs(true);
    try {
      const res = await ragApi.getDocuments(page, size);
      setDocuments(res.items);
      setTotalDocs(res.total);
    } catch (error) {
      logger.error('Failed to fetch documents', error);
      message.error('無法載入文件列表');
    } finally {
      setLoadingDocs(false);
    }
  };

  useEffect(() => {
    fetchDocuments();
  }, []);
  
  const handleUpload = async () => {
    if (fileList.length === 0) return;
    
    setUploading(true);
    const file = fileList[0];
    
    try {
      const userId = user?.id || '1';
      
      await ragApi.ingestDocument(file.originFileObj as File, userId, file.name);
      message.success('文件上傳並處理成功！');
      setFileList([]);
      fetchDocuments(); // Refresh list
    } catch (error) {
      logger.error('Upload document failed', error);
      message.error('文件上傳失敗');
    } finally {
      setUploading(false);
    }
  };

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const handleTextIngest = async (values: any) => {
    try {
      setUploading(true);
      const userId = user?.id || '1';
      await ragApi.ingestText({
        text: values.text,
        title: values.title,
        user_id: userId,
        source_type: 'note',
        tags: values.tags
      });
      message.success('文字內容已寫入知識庫！');
      textForm.resetFields();
      fetchDocuments(); // Refresh list
    } catch (error) {
      logger.error('Ingest text failed', error);
      message.error('寫入失敗');
    } finally {
      setUploading(false);
    }
  };

  const uploadProps = {
    onRemove: (file: UploadFile) => {
      setFileList((prev) => {
        const index = prev.indexOf(file);
        const newFileList = prev.slice();
        newFileList.splice(index, 1);
        return newFileList;
      });
    },
    beforeUpload: (file: UploadFile) => {
      setFileList([file]); // Single file upload for now to keep it simple
      return false;
    },
    fileList,
  };

  const handleDeleteDocument = async (id: string) => {
    try {
      await ragApi.deleteDocument(id);
      message.success('文件已刪除');
      fetchDocuments();
    } catch (error) {
      logger.error('Delete document failed', error);
      message.error('刪除失敗');
    }
  };

  const documentColumns = [
    {
      title: '標題',
      dataIndex: 'title',
      key: 'title',
      render: (text: string) => <span style={{ fontWeight: 500 }}>{text}</span>,
    },
    {
      title: '類型',
      dataIndex: 'sourceType',
      key: 'sourceType',
      render: (type: string) => <Tag color={type === 'UPLOAD' ? 'blue' : 'green'}>{type}</Tag>,
    },
    {
      title: '建立時間',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (text: string) => formatDateTime(text),
    },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: RagDocument) => (
        <Popconfirm title="確定刪除此文件？" onConfirm={() => handleDeleteDocument(record.id)}>
          <Button type="text" danger icon={<DeleteOutlined />} />
        </Popconfirm>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <Alert
        message="關於文件管理"
        description="您可以上傳 PDF/Markdown 文件或直接輸入文字筆記。系統會自動將其切分並存入向量資料庫，供 AI 助理檢索使用。"
        type="info"
        showIcon
        className="mb-4"
      />

      <Tabs
        defaultActiveKey="list"
        items={[
          {
            key: 'list',
            label: (<span><DatabaseOutlined /> 已上傳文件</span>),
            children: (
              <Card title="知識庫文件列表" extra={<Button icon={<ReloadOutlined />} onClick={() => fetchDocuments()}>重新整理</Button>}>
                <Table
                  dataSource={documents}
                  columns={documentColumns}
                  rowKey="id"
                  loading={loadingDocs}
                  pagination={{
                    total: totalDocs,
                    pageSize: 10,
                    onChange: (page) => fetchDocuments(page),
                  }}
                />
              </Card>
            )
          },
          {
            key: 'file',
            label: '文件上傳',
            children: (
              <Card title="上傳文件 (PDF / Markdown)">
                <Dragger {...uploadProps} accept=".pdf,.md,.txt">
                  <p className="ant-upload-drag-icon">
                    <InboxOutlined />
                  </p>
                  <p className="ant-upload-text">點擊或拖路文件至此區域上傳</p>
                  <p className="ant-upload-hint">
                    支援單個 PDF、Markdown 或 Text 檔案上傳。
                  </p>
                </Dragger>
                <div className="mt-4 flex justify-end">
                  <Button
                    type="primary"
                    onClick={handleUpload}
                    disabled={fileList.length === 0}
                    loading={uploading}
                  >
                    {uploading ? '處理中...' : '開始上傳處理'}
                  </Button>
                </div>
              </Card>
            ),
          },
          {
            key: 'text',
            label: '文字輸入',
            children: (
              <Card title="直接輸入文字內容">
                <Form
                  form={textForm}
                  layout="vertical"
                  onFinish={handleTextIngest}
                >
                  <Form.Item
                    name="title"
                    label="標題"
                    rules={[{ required: true, message: '請輸入標題' }]}
                  >
                    <Input placeholder="例如：2024年Q1會議記錄" />
                  </Form.Item>
                  <Form.Item
                    name="tags"
                    label="標籤"
                  >
                    <Input placeholder="例如：會議, 財報 (以逗號分隔)" />
                  </Form.Item>
                  <Form.Item
                    name="text"
                    label="內容"
                    rules={[{ required: true, message: '請輸入內容' }]}
                  >
                    <TextArea rows={10} placeholder="在此輸入或貼上文字內容..." />
                  </Form.Item>
                  <Form.Item>
                    <Button type="primary" htmlType="submit" loading={uploading} icon={<FileTextOutlined />}>
                      寫入知識庫
                    </Button>
                  </Form.Item>
                </Form>
              </Card>
            ),
          },
        ]}
      />
    </div>
  );
};

export default DocumentManager;
