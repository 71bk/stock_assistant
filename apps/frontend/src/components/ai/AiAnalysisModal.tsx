import React from 'react';
import { Modal, Divider, Spin, Empty, Button } from 'antd';
import { RobotOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { useAiStore } from '../../stores/ai.store';
import { AiReportViewer } from './AiReportViewer';

interface AiAnalysisModalProps {
  open: boolean;
  onClose: () => void;
  title?: string;
}

export const AiAnalysisModal: React.FC<AiAnalysisModalProps> = ({ open, onClose, title = "AI 投資分析" }) => {
  const { analysisStream, isAnalyzing } = useAiStore();

  return (
    <Modal
      title={
        <span>
          <RobotOutlined style={{ marginRight: 8, color: '#1677ff' }} />
          {title}
        </span>
      }
      open={open}
      onCancel={onClose}
      footer={[
        <Button key="close" onClick={onClose} type="primary">
          關閉
        </Button>,
      ]}
      width={700}
      styles={{ body: { minHeight: 400, maxHeight: '70vh', overflowY: 'auto' } }}
    >
      <div style={{ padding: '10px 0', lineHeight: 1.6 }}>
        {!analysisStream && isAnalyzing && (
          <div style={{ textAlign: 'center', padding: '50px 0' }}>
            <Spin tip="AI 正在思考中..." size="large" />
          </div>
        )}
        
        {analysisStream ? (
          <div className="report-container">
            <AiReportViewer content={analysisStream} />
            {isAnalyzing && (
              <div style={{ marginTop: 8, textAlign: 'center' }}>
                <Spin size="small" tip="正在產生報告內容..." />
              </div>
            )}
          </div>
        ) : !isAnalyzing ? (
          <Empty description="尚未開始分析" />
        ) : null}
      </div>
      
      <Divider />
      <div style={{ fontSize: '12px', color: '#8c8c8c' }}>
        <ThunderboltOutlined style={{ marginRight: 4 }} />
        AI 生成內容僅供參考，不構成投資建議。
      </div>
    </Modal>
  );
};
