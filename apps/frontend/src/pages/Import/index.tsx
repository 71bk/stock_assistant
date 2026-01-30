import React, { useState } from 'react';
import {
  Steps, Card, Upload, Typography, Button, Progress, Table,
  Tag, Tooltip, Space, Popconfirm, InputNumber, DatePicker, Select,
  Alert, Result, message
} from 'antd';
import {
  InboxOutlined, FileImageOutlined, CheckCircleOutlined,
  ExclamationCircleOutlined, DeleteOutlined, SaveOutlined,
  ReloadOutlined
} from '@ant-design/icons';
import { useImportStore } from '../../stores/import.store';
import type { DraftTrade } from '../../api/ocr.api';
import dayjs from 'dayjs';
import { useNavigate } from 'react-router-dom';

const { Dragger } = Upload;
const { Title, Text } = Typography;

// --- Step 0: Upload ---
const UploadStep: React.FC = () => {
  const { uploadFile } = useImportStore();

  const props = {
    name: 'file',
    multiple: false,
    showUploadList: false,
    accept: '.jpg,.jpeg,.pdf',
    customRequest: async (options: any) => {
      const { file, onSuccess } = options;
      await uploadFile(file);
      onSuccess("ok");
    },
  };

  return (
    <Card style={{ textAlign: 'center', padding: 40 }}>
      <Dragger {...props} style={{ padding: 40, background: '#fafafa' }}>
        <p className="ant-upload-drag-icon">
          <InboxOutlined style={{ fontSize: 48, color: '#1677ff' }} />
        </p>
        <p className="ant-upload-text">點擊或拖曳檔案至此上傳</p>
        <p className="ant-upload-hint">
          支援圖片 (JPG, JPEG) 或 PDF，OCR 將自動辨識交易細節
        </p>
      </Dragger>
    </Card>
  );
};

// --- Step 1: Processing ---
const ProcessingStep: React.FC = () => {
  const { progress, jobStatus } = useImportStore();

  return (
    <Card style={{ textAlign: 'center', padding: 60 }}>
      <Title level={4}>文件解析中...</Title>
      <Progress type="circle" percent={progress} status={jobStatus === 'FAILED' ? 'exception' : undefined} />
      <div style={{ marginTop: 20 }}>
        {jobStatus === 'FAILED' ? (
          <Text type="danger">OCR 解析失敗，請嘗試其他影像。</Text>
        ) : (
          <Text type="secondary">正在辨識日期、代號與金額...</Text>
        )}
      </div>
    </Card>
  );
};

// --- Step 2: Review ---
const ReviewStep: React.FC = () => {
  const { draftTrades, updateDraftTrade, deleteDraftTrade, confirmTrades, reprocessJob } = useImportStore();
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>(
    draftTrades.filter(t => t.status !== 'ERROR').map(t => t.draftId) // Select valid ones by default
  );
  const [editingKey, setEditingKey] = useState<string>('');

  const isEditing = (record: DraftTrade) => record.draftId === editingKey;

  const edit = (record: DraftTrade) => {
    setEditingKey(record.draftId);
  };

  const save = (id: string) => {
    // In a real form, we'd validate and gather values.
    // Here we assume the input onChange handlers updated the store or local state.
    // Since we are using store directly in render for simplicity, we just exit edit mode.
    setEditingKey('');
  };

  const columns = [
    {
      title: '狀態',
      key: 'status',
      width: 80,
      render: (_: any, record: DraftTrade) => {
        if (record.status === 'VALID') return <CheckCircleOutlined style={{ color: '#52c41a' }} />;
        if (record.status === 'WARNING') return <Tooltip title={record.warnings.join(', ')}><ExclamationCircleOutlined style={{ color: '#faad14' }} /></Tooltip>;
        return <Tooltip title={record.warnings.join(', ')}><ExclamationCircleOutlined style={{ color: '#f5222d' }} /></Tooltip>;
      },
    },
    {
      title: '日期',
      dataIndex: 'tradeDate',
      render: (text: string, record: DraftTrade) => {
        if (isEditing(record)) {
          return <DatePicker defaultValue={dayjs(text)} onChange={(d) => d && updateDraftTrade(record.draftId, { tradeDate: d.format('YYYY-MM-DD') })} />;
        }
        return text;
      }
    },
    {
      title: '代號',
      dataIndex: 'rawTicker',
      render: (text: string, record: DraftTrade) => {
        if (isEditing(record)) return <input value={text} onChange={(e) => updateDraftTrade(record.draftId, { rawTicker: e.target.value })} style={{ width: 80 }} />;
        return <Text strong>{text}</Text>;
      }
    },
    {
      title: '名稱',
      dataIndex: 'name',
      render: (text: string) => <Text type="secondary" style={{ fontSize: 12 }}>{text}</Text>,
    },
    {
      title: '方向',
      dataIndex: 'side',
      render: (text: string, record: DraftTrade) => {
        if (isEditing(record)) {
          return (
            <Select value={text} onChange={(v) => updateDraftTrade(record.draftId, { side: v as 'BUY' | 'SELL' })}>
              <Select.Option value="BUY">BUY</Select.Option>
              <Select.Option value="SELL">SELL</Select.Option>
            </Select>
          );
        }
        return <Tag color={text === 'BUY' ? 'blue' : 'volcano'}>{text}</Tag>;
      }
    },
    {
      title: '數量',
      dataIndex: 'quantity',
      render: (val: number, record: DraftTrade) => {
        if (isEditing(record)) return <InputNumber value={val} onChange={(v) => v && updateDraftTrade(record.draftId, { quantity: v })} style={{ width: 80 }} />;
        return val;
      }
    },
    {
      title: '價格',
      dataIndex: 'price',
      render: (val: number, record: DraftTrade) => {
        if (isEditing(record)) return <InputNumber value={val} onChange={(v) => v && updateDraftTrade(record.draftId, { price: v })} style={{ width: 100 }} />;
        return Number(val || 0).toFixed(2);
      }
    },
    {
      title: '幣別',
      dataIndex: 'currency',
      width: 80,
      render: (text: string) => <Tag>{text}</Tag>,
    },
    {
      title: '手續費',
      dataIndex: 'fee',
      render: (val: number, record: DraftTrade) => {
        if (isEditing(record)) return <InputNumber value={val} onChange={(v) => v != null && updateDraftTrade(record.draftId, { fee: v })} style={{ width: 80 }} />;
        return Number(val || 0).toFixed(2);
      }
    },
    {
      title: '稅金',
      dataIndex: 'tax',
      render: (val: number, record: DraftTrade) => {
        if (isEditing(record)) return <InputNumber value={val} onChange={(v) => v != null && updateDraftTrade(record.draftId, { tax: v })} style={{ width: 80 }} />;
        return Number(val || 0).toFixed(2);
      }
    },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: DraftTrade) => {
        const editable = isEditing(record);
        return editable ? (
          <Space>
            <Button type="primary" size="small" icon={<SaveOutlined />} onClick={() => save(record.draftId)} />
          </Space>
        ) : (
          <Space>
            <Button size="small" onClick={() => edit(record)}>編輯</Button>
            <Popconfirm title="刪除?" onConfirm={() => deleteDraftTrade(record.draftId)}>
              <Button size="small" danger icon={<DeleteOutlined />} />
            </Popconfirm>
          </Space>
        );
      },
    },
  ];

  const handleConfirm = () => {
    confirmTrades(selectedRowKeys as string[]);
  };

  return (
    <div>
      <Alert
        message={`找到 ${draftTrades.length} 筆交易`}
        description="請校對以下細節，確認無誤後再匯入。"
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
      />

      <Table
        rowSelection={{
          selectedRowKeys,
          onChange: (keys) => setSelectedRowKeys(keys),
        }}
        dataSource={draftTrades}
        columns={columns}
        rowKey="draftId"
        pagination={false}
        scroll={{ x: 800 }}
      />

      <div style={{ marginTop: 24, textAlign: 'right' }}>
        <Space>
          <Text>已選: {selectedRowKeys.length}</Text>
          <Button icon={<ReloadOutlined />} onClick={reprocessJob}>
            重新辨識
          </Button>
          <Button type="primary" size="large" disabled={selectedRowKeys.length === 0} onClick={handleConfirm}>
            確認匯入
          </Button>
        </Space>
      </div>
    </div>
  );
};

// --- Step 3: Result ---
const ResultStep: React.FC = () => {
  const { reset } = useImportStore();
  const navigate = useNavigate();

  return (
    <Result
      status="success"
      title="匯入成功！"
      subTitle="交易紀錄已儲存，您的投資組合部位已更新。"
      extra={[
        <Button type="primary" key="portfolio" onClick={() => navigate('/portfolio')}>
          前往投資組合
        </Button>,
        <Button key="import" onClick={reset}>
          匯入其他
        </Button>,
      ]}
    />
  );
};


// --- Main Page ---
const ImportPage: React.FC = () => {
  const { currentStep, jobStatus } = useImportStore();
  const { setStep } = useImportStore(); // Actually setStep isn't used directly by steps usually, managed by store flow

  // Determine which component to render
  let content = <UploadStep />;
  if (currentStep === 1) {
    if (jobStatus === 'DONE') {
      content = <ReviewStep />;
    } else {
      content = <ProcessingStep />;
    }
  } else if (currentStep === 2) {
    content = <ResultStep />;
  }

  return (
    <div>
      <Title level={2}>匯入交易</Title>
      <Card>
        <Steps
          current={currentStep}
          items={[
            { title: '上傳' },
            { title: '解析與校對' },
            { title: '完成' },
          ]}
          style={{ marginBottom: 40, maxWidth: 800, margin: '0 auto 40px' }}
        />
        {content}
      </Card>
    </div>
  );
};

export default ImportPage;
