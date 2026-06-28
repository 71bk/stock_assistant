import React, { useState, useEffect } from 'react';
import {
  Steps, Card, Upload, Typography, Button, Progress, Table,
  Tag, Tooltip, Space, Popconfirm, InputNumber, DatePicker, Select,
  Alert, Result, message, Input, Flex, Modal, theme
} from 'antd';
import {
  InboxOutlined, CheckCircleOutlined,
  ExclamationCircleOutlined, DeleteOutlined, SaveOutlined,
  ReloadOutlined, UploadOutlined, ImportOutlined, FileImageOutlined, LockOutlined
} from '@ant-design/icons';
import { useImportFlow } from '../../hooks/useImportFlow';
import { usePortfolioStore } from '../../stores/portfolio.store';
import type { DraftTrade } from '../../api/ocr.api';
import type { GetProp, UploadProps } from 'antd';
import dayjs from 'dayjs';
import { useNavigate } from 'react-router-dom';
import { PageContainer } from '../../components/layout/PageContainer';
import { DocumentPreviewModal } from './components/DocumentPreviewModal';
import {
  formatOcrMessage,
  formatOcrMessages,
  isInformationalOcrWarning,
} from '../../utils/ocrMessages';
const { Dragger } = Upload;
const { Title, Text } = Typography;

// --- Step 0: Upload ---
const UploadStep: React.FC = () => {
  const { token } = theme.useToken();
  const { uploadFile } = useImportFlow();
  const portfolios = usePortfolioStore((s) => s.portfolios);
  const currentPortfolioId = usePortfolioStore((s) => s.currentPortfolioId);
  const initPortfolioId = usePortfolioStore((s) => s.initPortfolioId);
  const [targetId, setTargetId] = useState<string | undefined>(currentPortfolioId ?? undefined);

  // Ensure the portfolio list is loaded and default the target to the active one.
  useEffect(() => {
    initPortfolioId();
  }, [initPortfolioId]);
  useEffect(() => {
    if (currentPortfolioId && !targetId) setTargetId(currentPortfolioId);
  }, [currentPortfolioId, targetId]);

  const props = {
    name: 'file',
    multiple: false,
    showUploadList: false,
    accept: '.jpg,.jpeg,.pdf',
    customRequest: async (options: Parameters<GetProp<UploadProps, 'customRequest'>>[0]) => {
      const { file, onSuccess } = options;
      // Pass the chosen target; when the user has no portfolio yet, leaving it
      // undefined lets uploadFile open the create-portfolio modal first.
      await uploadFile(file as File, targetId);
      onSuccess?.("ok");
    },
  };

  return (
    <Card style={{ textAlign: 'center', padding: 40 }}>
      {portfolios.length > 0 && (
        <div style={{ maxWidth: 360, margin: '0 auto 24px', textAlign: 'left' }}>
          <Text strong style={{ display: 'block', marginBottom: 8 }}>匯入到投資組合</Text>
          <Select
            style={{ width: '100%' }}
            value={targetId}
            onChange={setTargetId}
            options={portfolios.map((p) => ({ value: String(p.id), label: p.name }))}
            aria-label="選擇匯入目標投資組合"
          />
        </div>
      )}
      <Dragger {...props} style={{ padding: 40, background: token.colorFillTertiary }}>
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
const { progress, jobStatus, reset, reprocessJob, cancelJob, errorMessage, providePassword, isLoading } = useImportFlow();
const [password, setPassword] = useState('');

// 若 jobStatus 為 CANCELLED，也視為一種失敗狀態，讓使用者可以重試或重傳
const isFailedOrCancelled = jobStatus === 'FAILED' || jobStatus === 'CANCELLED';
const isPasswordRequired = jobStatus === 'PASSWORD_REQUIRED' || jobStatus === 'PASSWORD_INVALID';

let title = '文件解析中...';
if (jobStatus === 'FAILED') title = '解析失敗';
if (jobStatus === 'CANCELLED') title = '任務已取消';
if (isPasswordRequired) title = '需要密碼以解鎖 PDF';

const handlePasswordSubmit = () => {
  if (isLoading || !isPasswordRequired) {
    return;
  }
  if (password.trim()) {
    providePassword(password.trim());
  }
};

return (
  <>
    <Card style={{ textAlign: 'center', padding: 60 }}>
      <Title level={4}>{title}</Title>
      <Progress
        type="circle"
        percent={isPasswordRequired ? 0 : progress}
        status={isFailedOrCancelled ? 'exception' : (isPasswordRequired ? 'normal' : undefined)}
      />
      <div style={{ marginTop: 20 }}>
        {isFailedOrCancelled ? (
          <Flex vertical gap="middle" align="center">
            <Text type={jobStatus === 'FAILED' ? "danger" : "secondary"}>
              {jobStatus === 'FAILED'
                ? formatOcrMessage(errorMessage || 'OCR 解析失敗，請嘗試其他影像或重試。', 'error')
                : '您已取消此任務。'}
            </Text>
            <Flex gap="small">
              <Button icon={<ReloadOutlined />} onClick={reprocessJob}>
                重新嘗試
              </Button>
              <Button type="primary" icon={<UploadOutlined />} onClick={reset}>
                重新上傳
              </Button>
            </Flex>
          </Flex>
        ) : (
          <Flex vertical gap="middle" align="center">
            <Text type="secondary">{isPasswordRequired ? '正在等待輸入密碼...' : '正在辨識日期、代號與金額...'}</Text>
            <Flex gap="small">
              {!isPasswordRequired && (
                <Button icon={<ReloadOutlined />} onClick={reprocessJob} size="small">
                  重新解析
                </Button>
              )}
              <Button danger onClick={() => cancelJob()} size="small">
                取消任務
              </Button>
            </Flex>
          </Flex>
        )}
      </div>
    </Card>

    <Modal
      title="需要 PDF 密碼"
      open={isPasswordRequired}
      closable={false}
      maskClosable={false}
      footer={[
        <Button key="cancel" onClick={cancelJob} disabled={isLoading}>取消任務</Button>,
        <Button
          key="submit"
          type="primary"
          onClick={handlePasswordSubmit}
          loading={isLoading}
          disabled={!password.trim() || !isPasswordRequired}
        >
          確認
        </Button>
      ]}
    >
      <Flex vertical gap="middle">
        <Alert title="此文件受到密碼保護，請輸入密碼以繼續解析。" type="warning" showIcon />
        {jobStatus === 'PASSWORD_INVALID' && (
          <Alert
            title={formatOcrMessage(errorMessage || 'PDF 密碼錯誤，請重新輸入', 'error')}
            type="error"
            showIcon
          />
        )}
        <Input.Password
          placeholder="請輸入密碼"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          onPressEnter={handlePasswordSubmit}
          prefix={<LockOutlined />}
          disabled={isLoading}
          autoFocus
        />
      </Flex>
    </Modal>
  </>
);
};

// --- Step 2: Review ---
const ReviewStep: React.FC = () => {
  const { draftTrades, fileId, updateDraftTrade, deleteDraftTrade, confirmTrades, reprocessJob, isLoading, currentStep } = useImportFlow();
  const [isPreviewModalVisible, setIsPreviewModalVisible] = useState(false);

  // 預設選取邏輯：狀態為 VALID 且 非重複 的草稿
  // 若使用者想匯入重複或警告的交易，需手動勾選
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>(
    draftTrades
      .filter(t => t.status === 'VALID' && !t.duplicate)
      .map(t => t.draftId)
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
        const warnings = [...(record.warnings || [])];
        if (record.duplicate) warnings.push('該交易可能已存在於資料庫中 (重複交易)');
        
        const formattedWarnings = formatOcrMessages(warnings);
        const formattedErrors = formatOcrMessages(record.errors, 'error');

        // 1. 優先顯示明確的錯誤
        if (record.status === 'ERROR') {
          return (
            <Tooltip title={formattedErrors.join(', ')}>
              <Tag icon={<ExclamationCircleOutlined />} color="error">錯誤</Tag>
            </Tooltip>
          );
        }

        // 2. 顯示重複交易 (視為警告的一種)
        if (record.duplicate) {
          return (
            <Tooltip title="偵測到資料庫中已有相同內容的交易紀錄">
              <Tag icon={<ExclamationCircleOutlined />} color="warning">重複交易</Tag>
            </Tooltip>
          );
        }

        // 3. 顯示一般警告
        if (record.status === 'WARNING') {
          const isSettlementError = record.warnings.includes('SETTLEMENT_BEFORE_TRADE');
          const onlyInformationalWarnings = record.warnings.length > 0
            && record.warnings.every(isInformationalOcrWarning);
          
          let label = '警告';
          if (isSettlementError) {
            label = '日期錯誤';
          } else if (onlyInformationalWarnings) {
            label = '請確認資訊';
          }

          return (
            <Tooltip title={formattedWarnings.join(', ')}>
              <Tag icon={<ExclamationCircleOutlined />} color="warning">
                {label}
              </Tag>
            </Tooltip>
          );
        }

        // 4. 預設顯示正常 (若非 Error/Warning/Duplicate，則視為 Valid)
        return <Tag icon={<CheckCircleOutlined />} color="success">正常</Tag>;
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
      width: 120,
      render: (text: string, record: DraftTrade) => {
        if (isEditing(record)) return <Input value={text} onChange={(e) => updateDraftTrade(record.draftId, { rawTicker: e.target.value })} style={{ width: '100%' }} />;
        return <Text strong>{text}</Text>;
      }
    },
    {
      title: '名稱',
      dataIndex: 'name',
      width: 100,
      render: (text: string) => <Text type="secondary" style={{ fontSize: 12 }}>{text}</Text>,
    },
    {
      title: '方向',
      dataIndex: 'side',
      width: 100,
      render: (text: string, record: DraftTrade) => {
        if (isEditing(record)) {
          return (
            <Select value={text} onChange={(v) => updateDraftTrade(record.draftId, { side: v as 'BUY' | 'SELL' })} style={{ width: '100%' }}>
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
      width: 120,
      render: (val: number, record: DraftTrade) => {
        if (isEditing(record)) return <InputNumber value={val} min={0} onChange={(v) => v != null && updateDraftTrade(record.draftId, { quantity: v })} style={{ width: '100%' }} />;
        return val;
      }
    },
    {
      title: '價格',
      dataIndex: 'price',
      width: 140,
      render: (val: number, record: DraftTrade) => {
        if (isEditing(record)) return <InputNumber value={val} min={0} onChange={(v) => v != null && updateDraftTrade(record.draftId, { price: v })} style={{ width: '100%' }} />;
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
      width: 120,
      render: (val: number, record: DraftTrade) => {
        if (isEditing(record)) return <InputNumber value={val} min={0} onChange={(v) => v != null && updateDraftTrade(record.draftId, { fee: v })} style={{ width: '100%' }} />;
        return Number(val || 0).toFixed(2);
      }
    },
    {
      title: '稅金',
      dataIndex: 'tax',
      width: 120,
      render: (val: number, record: DraftTrade) => {
        if (isEditing(record)) return <InputNumber value={val} min={0} onChange={(v) => v != null && updateDraftTrade(record.draftId, { tax: v })} style={{ width: '100%' }} />;
        return Number(val || 0).toFixed(2);
      }
    },
    {
      title: '淨收付',
      dataIndex: 'netAmount',
      width: 120,
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

  const isAbnormal = (t: DraftTrade) => {
    if (t.status === 'ERROR') return true;
    if (t.duplicate) return true;
    if (t.status === 'WARNING') {
      return t.warnings.some(w => !isInformationalOcrWarning(w));
    }
    return false;
  };

  const abnormalTrades = draftTrades.filter(isAbnormal);

  return (
    <div>
      <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'flex-end' }}>
        <Button icon={<FileImageOutlined />} onClick={() => setIsPreviewModalVisible(true)}>
          預覽原始文件
        </Button>
      </div>

      {abnormalTrades.length > 0 ? (
        <Alert
          title="發現異常資料"
          description={`在 ${draftTrades.length} 筆交易中，有 ${abnormalTrades.length} 筆資料可能重複或有誤，請仔細檢查標紅區域。`}
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
        scroll={{ x: 1800, y: 600 }}
        rowClassName={(record) => {
          if (record.status === 'ERROR') return 'row-error';
          if (record.duplicate) return 'row-warning row-duplicate';
          // 如果只是 fallback warning，不要把整行標黃色
          if (record.status === 'WARNING') {
            return isAbnormal(record) ? 'row-warning' : '';
          }
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

      <DocumentPreviewModal
        fileId={fileId}
        open={isPreviewModalVisible}
        onClose={() => setIsPreviewModalVisible(false)}
      />
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
    <PageContainer>
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
    </PageContainer>
  );
};

export default ImportPage;
