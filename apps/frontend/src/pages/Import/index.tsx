import React, { useState } from 'react';
import {
  Steps, Card, Upload, Typography, Button, Progress, Table,
  Tag, Tooltip, Space, Popconfirm, InputNumber, DatePicker, Select,
  Alert, Result, message
} from 'antd';
import {
  InboxOutlined, FileImageOutlined, CheckCircleOutlined,
  ExclamationCircleOutlined, DeleteOutlined, SaveOutlined
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
        <p className="ant-upload-text">Click or drag file to this area to upload</p>
        <p className="ant-upload-hint">
          Support for images (JPG, PNG) or PDF. OCR will automatically extract trade details.
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
      <Title level={4}>Analyzing Document...</Title>
      <Progress type="circle" percent={progress} status={jobStatus === 'FAILED' ? 'exception' : undefined} />
      <div style={{ marginTop: 20 }}>
        {jobStatus === 'FAILED' ? (
          <Text type="danger">OCR Analysis Failed. Please try another image.</Text>
        ) : (
          <Text type="secondary">Identifying dates, symbols, and amounts...</Text>
        )}
      </div>
    </Card>
  );
};

// --- Step 2: Review ---
const ReviewStep: React.FC = () => {
  const { draftTrades, updateDraftTrade, deleteDraftTrade, confirmTrades } = useImportStore();
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>(
    draftTrades.filter(t => t.status !== 'ERROR').map(t => t.id) // Select valid ones by default
  );
  const [editingKey, setEditingKey] = useState<string>('');

  const isEditing = (record: DraftTrade) => record.id === editingKey;

  const edit = (record: DraftTrade) => {
    setEditingKey(record.id);
  };

  const save = (id: string) => {
    // In a real form, we'd validate and gather values.
    // Here we assume the input onChange handlers updated the store or local state.
    // Since we are using store directly in render for simplicity, we just exit edit mode.
    setEditingKey('');
  };

  const columns = [
    {
      title: 'Status',
      key: 'status',
      width: 80,
      render: (_: any, record: DraftTrade) => {
        if (record.status === 'VALID') return <CheckCircleOutlined style={{ color: '#52c41a' }} />;
        if (record.status === 'WARNING') return <Tooltip title={record.warnings.join(', ')}><ExclamationCircleOutlined style={{ color: '#faad14' }} /></Tooltip>;
        return <Tooltip title={record.warnings.join(', ')}><ExclamationCircleOutlined style={{ color: '#f5222d' }} /></Tooltip>;
      },
    },
    {
      title: 'Date',
      dataIndex: 'tradeDate',
      render: (text: string, record: DraftTrade) => {
        if (isEditing(record)) {
          return <DatePicker defaultValue={dayjs(text)} onChange={(d) => d && updateDraftTrade(record.id, { tradeDate: d.format('YYYY-MM-DD') })} />;
        }
        return text;
      }
    },
    {
      title: 'Symbol',
      dataIndex: 'symbol',
      render: (text: string, record: DraftTrade) => {
        if (isEditing(record)) return <input value={text} onChange={(e) => updateDraftTrade(record.id, { symbol: e.target.value })} style={{ width: 80 }} />;
        return <Text strong>{text}</Text>;
      }
    },
    {
      title: 'Side',
      dataIndex: 'side',
      render: (text: string, record: DraftTrade) => {
        if (isEditing(record)) {
          return (
            <Select value={text} onChange={(v) => updateDraftTrade(record.id, { side: v as 'BUY' | 'SELL' })}>
              <Select.Option value="BUY">BUY</Select.Option>
              <Select.Option value="SELL">SELL</Select.Option>
            </Select>
          );
        }
        return <Tag color={text === 'BUY' ? 'blue' : 'volcano'}>{text}</Tag>;
      }
    },
    {
      title: 'Qty',
      dataIndex: 'quantity',
      render: (val: number, record: DraftTrade) => {
        if (isEditing(record)) return <InputNumber value={val} onChange={(v) => v && updateDraftTrade(record.id, { quantity: v })} style={{ width: 80 }} />;
        return val;
      }
    },
    {
      title: 'Price',
      dataIndex: 'price',
      render: (val: number, record: DraftTrade) => {
        if (isEditing(record)) return <InputNumber value={val} onChange={(v) => v && updateDraftTrade(record.id, { price: v })} style={{ width: 100 }} />;
        return val.toFixed(2);
      }
    },
    {
      title: 'Action',
      key: 'action',
      render: (_: any, record: DraftTrade) => {
        const editable = isEditing(record);
        return editable ? (
          <Space>
            <Button type="primary" size="small" icon={<SaveOutlined />} onClick={() => save(record.id)} />
          </Space>
        ) : (
          <Space>
            <Button size="small" onClick={() => edit(record)}>Edit</Button>
            <Popconfirm title="Delete?" onConfirm={() => deleteDraftTrade(record.id)}>
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
        message={`Found ${draftTrades.length} trades`}
        description="Please review the details below. Identify any warnings before confirming."
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
        rowKey="id"
        pagination={false}
        scroll={{ x: 800 }}
      />

      <div style={{ marginTop: 24, textAlign: 'right' }}>
        <Space>
          <Text>Selected: {selectedRowKeys.length}</Text>
          <Button type="primary" size="large" disabled={selectedRowKeys.length === 0} onClick={handleConfirm}>
            Confirm Import
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
      title="Import Successfully!"
      subTitle="Trades have been recorded and your portfolio positions updated."
      extra={[
        <Button type="primary" key="portfolio" onClick={() => navigate('/portfolio')}>
          Go Portfolio
        </Button>,
        <Button key="import" onClick={reset}>
          Import Another
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
    if (jobStatus === 'COMPLETED') {
      content = <ReviewStep />;
    } else {
      content = <ProcessingStep />;
    }
  } else if (currentStep === 2) {
    content = <ResultStep />;
  }

  return (
    <div>
      <Title level={2}>Import Trades</Title>
      <Card>
        <Steps
          current={currentStep}
          items={[
            { title: 'Upload' },
            { title: 'Processing & Review' }, // Combined conceptual step
            { title: 'Done' },
          ]}
          style={{ marginBottom: 40, maxWidth: 800, margin: '0 auto 40px' }}
        />
        {content}
      </Card>
    </div>
  );
};

export default ImportPage;
