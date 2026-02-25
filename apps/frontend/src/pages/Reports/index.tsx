import React, { useEffect, useState } from 'react';
import { Typography, Card, Table, Tag, Button, Modal, Skeleton } from 'antd';
import { FileSearchOutlined, RobotOutlined } from '@ant-design/icons';
import { useAiStore } from '../../stores/ai.store';
import { aiApi } from '../../api/ai.api';
import { ErrorState } from '../../components/common/ErrorState';
import { formatDateTime } from '../../utils/format';
import { AiReportViewer } from '../../components/ai/AiReportViewer';
import type { AiReportDetail, AiReportSummary } from '../../api/ai.api';
import { logger } from '../../utils/logger';

const { Title } = Typography;

const Reports: React.FC = () => {
  const { reports, totalReports, isLoading, error, fetchReports } = useAiStore();
  const [selectedReport, setSelectedReport] = useState<AiReportDetail | null>(null);
  const [isDetailLoading, setIsDetailLoading] = useState(false);
  const [isModalOpen, setIsModalOpen] = useState(false);

  useEffect(() => {
    fetchReports();
  }, [fetchReports]);

  const handleViewDetails = async (report: AiReportSummary) => {
    setIsModalOpen(true);
    setIsDetailLoading(true);
    try {
      const detail = await aiApi.getReport(report.reportId);
      setSelectedReport(detail);
    } catch (e) {
      logger.error('Failed to load report detail', e);
      setSelectedReport(null);
    } finally {
      setIsDetailLoading(false);
    }
  };

  if (error) {
    return (
      <ErrorState
        status="500"
        title="無法載入報告"
        message={error}
      />
    );
  }

  const columns = [
    {
      title: '日期',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (text: string) => formatDateTime(text),
    },
    {
      title: '類型',
      dataIndex: 'reportType',
      key: 'reportType',
      render: (type: string) => (
        <Tag color={type === 'PORTFOLIO' ? 'blue' : 'green'}>
          {type === 'PORTFOLIO' ? '組合分析' : '個股分析'}
        </Tag>
      ),
    },
    {
      title: '分析對象',
      key: 'target',
      render: (record: AiReportSummary) => {
        if (record.reportType === 'PORTFOLIO') return '預設投資組合';
        return record.instrumentId || '未知標的';
      },
    },
    {
      title: '操作',
      key: 'actions',
      render: (record: AiReportSummary) => (
        <Button
          icon={<FileSearchOutlined />}
          onClick={() => handleViewDetails(record)}
          size="small"
        >
          查看詳情
        </Button>
      ),
    },
  ];

  return (
    <div>
      <Title level={2}>
        <RobotOutlined style={{ marginRight: 8 }} />
        分析報告歷史
      </Title>
      
      <Card>
        {isLoading ? (
          <div style={{ padding: 20 }}>
            <Skeleton active paragraph={{ rows: 10 }} />
          </div>
        ) : (
          <Table
            dataSource={reports}
            columns={columns}
            rowKey="reportId"
            pagination={{
              total: totalReports,
              pageSize: 20,
              onChange: (page) => fetchReports(page),
            }}
          />
        )}
      </Card>

      <Modal
        title={
          <span>
            <FileSearchOutlined style={{ marginRight: 8 }} />
            報告詳情 - {selectedReport && formatDateTime(selectedReport.createdAt)}
          </span>
        }
        open={isModalOpen}
        onCancel={() => {
          setIsModalOpen(false);
          setSelectedReport(null);
        }}
        footer={[
          <Button
            key="close"
            onClick={() => {
              setIsModalOpen(false);
              setSelectedReport(null);
            }}
            type="primary"
          >
            關閉
          </Button>
        ]}
        width={800}
      >
        {isDetailLoading ? (
          <Skeleton active />
        ) : selectedReport ? (
          <div style={{ padding: '10px 0' }}>
            <div style={{ marginBottom: 16 }}>
              <Tag color={selectedReport.reportType === 'PORTFOLIO' ? 'blue' : 'green'}>
                {selectedReport.reportType === 'PORTFOLIO' ? '組合分析' : '個股分析'}
              </Tag>
            </div>
            <AiReportViewer content={selectedReport.outputText} />
          </div>
        ) : (
          <div style={{ color: '#8c8c8c' }}>報告內容不存在或已刪除。</div>
        )}
      </Modal>
    </div>
  );
};

export default Reports;
