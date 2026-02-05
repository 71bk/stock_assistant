import React, { useState } from 'react';
import {
  Steps, Card, Upload, Typography, Button, Progress, Table,
  Tag, Tooltip, Space, Popconfirm, InputNumber, DatePicker, Select,
  Alert, Result, message, Input
} from 'antd';
import {
  InboxOutlined, CheckCircleOutlined,
  ExclamationCircleOutlined, DeleteOutlined, SaveOutlined,
  ReloadOutlined, UploadOutlined, ImportOutlined
} from '@ant-design/icons';
import { useImportFlow } from '../../hooks/useImportFlow';
import type { DraftTrade } from '../../api/ocr.api';
import type { GetProp, UploadProps } from 'antd';
import dayjs from 'dayjs';
import { useNavigate } from 'react-router-dom';
const { Dragger } = Upload;
const { Title, Text } = Typography;

// --- Step 0: Upload ---
const UploadStep: React.FC = () => {
  const { uploadFile } = useImportFlow();

  const props = {
    name: 'file',
    multiple: false,
    showUploadList: false,
    accept: '.jpg,.jpeg,.pdf',
    customRequest: async (options: Parameters<GetProp<UploadProps, 'customRequest'>>[0]) => {
      const { file, onSuccess } = options;
      await uploadFile(file as File);
      onSuccess?.("ok");
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
  const { progress, jobStatus, reset, reprocessJob, cancelJob } = useImportFlow();

  return (
    <Card style={{ textAlign: 'center', padding: 60 }}>
      <Title level={4}>{jobStatus === 'FAILED' ? '解析失敗' : '文件解析中...'}</Title>
      <Progress type="circle" percent={progress} status={jobStatus === 'FAILED' ? 'exception' : undefined} />
      <div style={{ marginTop: 20 }}>
        {jobStatus === 'FAILED' ? (
          <Space orientation="vertical" size="middle">
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
          <Space orientation="vertical" size="middle">
            <Text type="secondary">正在辨識日期、代號與金額...</Text>
            <Space>
              <Button icon={<ReloadOutlined />} onClick={reprocessJob} size="small">
                重新解析
              </Button>
              <Button danger onClick={cancelJob} size="small">
                取消任務
              </Button>
            </Space>
          </Space>
        )}
      </div>
    </Card>
  );
};

// --- Step 2: Review ---
const ReviewStep: React.FC = () => {
  const { draftTrades, updateDraftTrade, deleteDraftTrade, confirmTrades, reprocessJob, isLoading, currentStep } = useImportFlow();
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>(
    draftTrades.filter(t => t.status !== 'ERROR').map(t => t.draftId) // Select valid ones by default
  );
  const [editingKey, setEditingKey] = useState<string>('');

  // Clear editing state when step changes
  React.useEffect(() => {
    setEditingKey('');
  }, [currentStep]);

  // Synchronize selection when draftTrades change (e.g., after a partial import success)
  React.useEffect(() => {
    const validDraftIds = draftTrades.map(t => t.draftId);
    setSelectedRowKeys(prev => prev.filter(key => validDraftIds.includes(key as string)));
  }, [draftTrades]);

  const isEditing = (record: DraftTrade) => record.draftId === editingKey;

  const edit = (record: DraftTrade) => {
    setEditingKey(record.draftId);
  };

  const handleSave = () => {
    // Input onChange handlers already updated the store.
    setEditingKey('');
  };

  const columns = [
    {
      title: '狀態',
      key: 'status',
      width: 110,
      render: (_: unknown, record: DraftTrade) => {
        if (record.status === 'VALID') return <Tag icon={<CheckCircleOutlined />} color="success">正常</Tag>;

        const formatMessage = (msg: string) => {
          if (msg === 'SETTLEMENT_BEFORE_TRADE') return '交割日不可早於成交日';
          return msg;
        };

        const warnings = (record.warnings || []).map(formatMessage);
        const errors = (record.errors || []).map(formatMessage);

        if (record.status === 'WARNING') {
          const isDuplicate = record.warnings.some(w => w.includes('重複'));
          const isSettlementError = record.warnings.includes('SETTLEMENT_BEFORE_TRADE');

          let label = '警告';
          if (isDuplicate) label = '可能重複';
          if (isSettlementError) label = '日期錯誤';

          return (
            <Tooltip title={warnings.join(', ')}>
              <Tag icon={<ExclamationCircleOutlined />} color="warning">
                {label}
              </Tag>
            </Tooltip>
          );
        }
        return (
          <Tooltip title={[...errors, ...warnings].join(', ')}>
            <Tag icon={<ExclamationCircleOutlined />} color="error">錯誤</Tag>
          </Tooltip>
        );
      },
    },
    {
      title: '成交日',
      dataIndex: 'tradeDate',
      width: 160,
      render: (text: string, record: DraftTrade) => {
        if (isEditing(record)) {
          return (
            <DatePicker
              style={{ width: '100%' }}
              value={record.tradeDate ? dayjs(record.tradeDate) : null}
              format="YYYY-MM-DD"
              placeholder="選擇日期"
              onChange={(d) => {
                if (d && d.isValid()) {
                  if (d.isAfter(dayjs())) {
                    message.error('成交日不可為未來日期，請重新選擇');
                    return;
                  }
                  const formatted = d.format('YYYY-MM-DD');
                  updateDraftTrade(record.draftId, { tradeDate: formatted });
                }
              }}
              allowClear
            />
          );
        }
        return text;
      }
    },
    {
      title: '交割日',
      dataIndex: 'settlementDate',
      width: 160,
      render: (text: string | null, record: DraftTrade) => {
        if (isEditing(record)) {
          return (
            <DatePicker
              style={{ width: '100%', minWidth: '110px' }}
              value={record.settlementDate ? dayjs(record.settlementDate) : null}
              format="YYYY-MM-DD"
              placeholder="選擇日期"
              disabledDate={(current) => {
                if (!record.tradeDate) return current.isAfter(dayjs());
                return current.isBefore(dayjs(record.tradeDate)) || current.isAfter(dayjs());
              }}
              onChange={(d) => updateDraftTrade(record.draftId, { settlementDate: d ? d.format('YYYY-MM-DD') : null })}
              allowClear
            />
          );
        }
        return text || '-';
      }
    },
    {
      title: '代號',
      dataIndex: 'rawTicker',
      render: (text: string, record: DraftTrade) => {
        if (isEditing(record)) return <Input value={text} onChange={(e) => updateDraftTrade(record.draftId, { rawTicker: e.target.value })} style={{ width: 100 }} />;
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
        if (isEditing(record)) return <InputNumber value={val} min={0} onChange={(v) => v != null && updateDraftTrade(record.draftId, { quantity: v })} style={{ width: 100 }} />;
        return val;
      }
    },
    {
      title: '價格',
      dataIndex: 'price',
      render: (val: number, record: DraftTrade) => {
        if (isEditing(record)) return <InputNumber value={val} min={0} onChange={(v) => v != null && updateDraftTrade(record.draftId, { price: v })} style={{ width: 120 }} />;
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
        if (isEditing(record)) return <InputNumber value={val} min={0} onChange={(v) => v != null && updateDraftTrade(record.draftId, { fee: v })} style={{ width: 100 }} />;
        return Number(val || 0).toFixed(2);
      }
    },
    {
      title: '稅金',
      dataIndex: 'tax',
      render: (val: number, record: DraftTrade) => {
        if (isEditing(record)) return <InputNumber value={val} min={0} onChange={(v) => v != null && updateDraftTrade(record.draftId, { tax: v })} style={{ width: 100 }} />;
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
      render: (_: unknown, record: DraftTrade) => {
        const editable = isEditing(record);
        return editable ? (
          <Space>
            <Button type="primary" size="small" icon={<SaveOutlined />} onClick={handleSave}>儲存</Button>
            <Button size="small" onClick={() => setEditingKey('')}>取消</Button>
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
          title="發現異常資料"
          description={`在 ${draftTrades.length} 筆交易中，有 ${draftTrades.filter(t => t.status !== 'VALID').length} 筆資料可能重複或有誤，請仔細檢查標紅區域。`}
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
        />
      ) : (
        <Alert
          title={`找到 ${draftTrades.length} 筆交易`}
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
        scroll={{ x: 1400 }}
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
  const { reset } = useImportFlow();
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
  const { currentStep, jobStatus } = useImportFlow();

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
