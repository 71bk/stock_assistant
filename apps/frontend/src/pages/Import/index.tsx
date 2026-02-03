import React, { useState } from 'react';
import {
  Steps, Card, Upload, Typography, Button, Progress, Table,
  Tag, Tooltip, Space, Popconfirm, InputNumber, DatePicker, Select,
  Alert, Result, message
} from 'antd';
import {
  InboxOutlined, FileImageOutlined, CheckCircleOutlined,
  ExclamationCircleOutlined, DeleteOutlined, SaveOutlined,
  ReloadOutlined, UploadOutlined
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
  const { progress, jobStatus, reset, reprocessJob } = useImportStore();

  return (
    <Card style={{ textAlign: 'center', padding: 60 }}>
      <Title level={4}>{jobStatus === 'FAILED' ? '解析失敗' : '文件解析中...'}</Title>
      <Progress type="circle" percent={progress} status={jobStatus === 'FAILED' ? 'exception' : undefined} />
      <div style={{ marginTop: 20 }}>
        {jobStatus === 'FAILED' ? (
          <Space direction="vertical" size="middle">
            <Text type="danger">OCR 解析失敗，請嘗試其他影像或重試。</Text>
            <Space>
              <Button icon={<ReloadOutlined />} onClick={reprocessJob}>
                重新嘗試
              </Button>
              <Button type="primary" icon={<UploadOutlined />} onClick={reset}>
                重新上傳
              </Button>
            </Space>
          </Space>
        ) : (
          <Text type="secondary">正在辨識日期、代號與金額...</Text>
        )}
      </div>
    </Card>
  );
};

// --- Step 2: Review ---
const ReviewStep: React.FC = () => {
  const { draftTrades, updateDraftTrade, deleteDraftTrade, confirmTrades, reprocessJob, isLoading } = useImportStore();
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
      width: 110,
      render: (_: any, record: DraftTrade) => {
        if (record.status === 'VALID') return <Tag icon={<CheckCircleOutlined />} color="success">正常</Tag>;
        if (record.status === 'WARNING') {
          const isDuplicate = record.warnings.some(w => w.includes('重複'));
          return (
            <Tooltip title={record.warnings.join(', ')}>
              <Tag icon={<ExclamationCircleOutlined />} color="warning">
                {isDuplicate ? '可能重複' : '警告'}
              </Tag>
            </Tooltip>
          );
        }
        return (
          <Tooltip title={record.warnings.join(', ')}>
            <Tag icon={<ExclamationCircleOutlined />} color="error">錯誤</Tag>
          </Tooltip>
        );
      },
    },
    {
      title: '成交日',
      dataIndex: 'tradeDate',
      render: (text: string, record: DraftTrade) => {
        if (isEditing(record)) {
          return <DatePicker defaultValue={dayjs(text)} onChange={(d) => d && updateDraftTrade(record.draftId, { tradeDate: d.format('YYYY-MM-DD') })} />;
        }
        return text;
      }
    },
    {
      title: '交割日',
      dataIndex: 'settlementDate',
      render: (text: string | null, record: DraftTrade) => {
        if (isEditing(record)) {
          return <DatePicker defaultValue={text ? dayjs(text) : undefined} onChange={(d) => updateDraftTrade(record.draftId, { settlementDate: d ? d.format('YYYY-MM-DD') : null } as any)} />;
        }
        return text || '-';
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
      title: '淨收付',
      dataIndex: 'netAmount',
      render: (val: number | null) => {
        if (val == null) return '-';
        const formatted = Math.abs(val).toLocaleString('zh-TW', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
        // 負數表示客戶淨付（買入），用紅色括號；正數表示客戶淨收（賣出），用綠色
        if (val < 0) {
          return <span style={{ color: '#cf1322' }}>({formatted})</span>;
        }
        return <span style={{ color: '#389e0d' }}>{formatted}</span>;
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
            <Tooltip title="單筆匯入">
              <Button
                size="small"
                type="primary"
                ghost
                icon={<ImportOutlined />}
                onClick={() => confirmTrades([record.draftId])}
                disabled={record.status === 'ERROR'}
              />
            </Tooltip>
            <Button size="small" onClick={() => edit(record)}>編輯</Button>
            <Popconfirm title="刪除此筆草稿?" onConfirm={() => deleteDraftTrade(record.draftId)}>
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
      {draftTrades.some(t => t.status !== 'VALID') ? (
        <Alert
          message="發現異常資料"
          description={`在 ${draftTrades.length} 筆交易中，有 ${draftTrades.filter(t => t.status !== 'VALID').length} 筆資料可能重複或有誤，請仔細檢查標紅區域。`}
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
        />
      ) : (
        <Alert
          message={`找到 ${draftTrades.length} 筆交易`}
          description="請校對以下細節，確認無誤後再匯入。"
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
        />
      )}

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
        rowClassName={(record) => {
          if (record.status === 'ERROR') return 'row-error';
          if (record.status === 'WARNING') return 'row-warning';
          return '';
        }}
      />

      <div style={{ marginTop: 24, textAlign: 'right' }}>
        <Space>
          <Text>已選: {selectedRowKeys.length}</Text>
          <Button icon={<ReloadOutlined />} onClick={reprocessJob}>
            重新辨識
          </Button>
          <Button type="primary" size="large" disabled={selectedRowKeys.length === 0} loading={isLoading} onClick={handleConfirm}>
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
