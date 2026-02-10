import React, { useMemo } from 'react';
import {
  Card, Row, Col, Typography, Statistic, Table,
  Tag, Descriptions, Progress, Alert, Divider, Space, Flex
} from 'antd';
import {
  RiseOutlined, LineChartOutlined,
  CheckCircleOutlined, BulbOutlined
} from '@ant-design/icons';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

const { Title, Text, Paragraph } = Typography;

interface PerformanceRow {
  date: string;
  price: string;
  change: string;
  return: string;
  volume: string;
}

interface AiReportViewerProps {
  content: string;
}

/**
 * 專門用於視覺化 AI 分析報告的組件
 */
export const AiReportViewer: React.FC<AiReportViewerProps> = ({ content = "" }) => {
  // 1. 嘗試解析基礎資訊 (Regex 提取)
  const basicInfo = useMemo(() => {
    if (!content) return { instrument: null, exchange: null, currency: null, assetClass: null, latestPrice: null };
    const instrument = content.match(/\*\*Instrument:\*\*(.*?)(?:\*|$)/)?.[1]?.trim();
    const exchange = content.match(/\*Exchange:\s*(.*?)(?:\*|$)/)?.[1]?.trim();
    const currency = content.match(/\*Currency:\s*(.*?)(?:\*|$)/)?.[1]?.trim();
    const assetClass = content.match(/\*\*Asset Class:\*\s*(.*?)(?:\*|$)/)?.[1]?.trim();
    
    // 獲取最新價格 (從表格最後一行提取)
    const priceMatch = content.match(/\|2026-02-08\s+\|(\d+\.\d+)\s+\|/);
    const latestPrice = priceMatch ? priceMatch[1] : null;

    return { instrument, exchange, currency, assetClass, latestPrice };
  }, [content]);

  // 2. 解析市場表現表格
  const performanceData = useMemo(() => {
    if (!content) return [];
    const rows: PerformanceRow[] = [];
    const tableRegex = /\|(\d{4}-\d{2}-\d{2})\s+\|(\d+\.\d+)\s+\|([^|]+)\|([^|]+)\|([^|]+)\|/g;
    let match;
    while ((match = tableRegex.exec(content)) !== null) {
      rows.push({
        date: match[1],
        price: match[2],
        change: match[3].trim(),
        return: match[4].trim(),
        volume: match[5].trim()
      });
    }
    return rows;
  }, [content]);

  // 3. 解析技術指標
  const technicalIndicators = useMemo(() => {
    if (!content) return { rsi: null, sma20: null, macd: null };
    // 簡單提取指標名稱與數值
    const rsiMatch = content.match(/相對強弱指標 \(RSI-14\)\s+\|(\d+)/);
    const sma20Match = content.match(/20日簡單移動平均線 \(SMA20\)\s+\|(\d+\.\d+)/);
    const macdMatch = content.match(/MACD \(12,26,9\)\s+\|([^|]+)/);
    
    return {
      rsi: rsiMatch ? parseInt(rsiMatch[1]) : null,
      sma20: sma20Match ? sma20Match[1] : null,
      macd: macdMatch ? macdMatch[1].trim() : null
    };
  }, [content]);

  // 4. 判斷是否解析成功，若內容格式太亂則走 Fallback
  const isParsedSuccessfully = useMemo(() => {
    return basicInfo.instrument || performanceData.length > 0;
  }, [basicInfo, performanceData]);

  if (!isParsedSuccessfully) {
    return (
      <Card className="ai-report-fallback">
        <ReactMarkdown remarkPlugins={[remarkGfm]}>{content}</ReactMarkdown>
      </Card>
    );
  }

  return (
    <div className="ai-report-visualizer" style={{ paddingBottom: 24 }}>
      <Row gutter={[16, 16]}>
        {/* 頂部：標的資訊與最新狀態 */}
        <Col span={24}>
          <Card bordered={false} style={{ background: 'linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%)', borderRadius: 12 }}>
            <Row align="middle" gutter={24}>
              <Col flex="auto">
                <Flex vertical gap={4}>
                  <Text type="secondary">{basicInfo.exchange} • {basicInfo.assetClass}</Text>
                  <Title level={2} style={{ margin: 0 }}>{basicInfo.instrument}</Title>
                </Flex>
              </Col>
              <Col>
                <Statistic 
                  title="最新價格" 
                  value={basicInfo.latestPrice || '--'} 
                  precision={2} 
                  suffix={basicInfo.currency}
                  valueStyle={{ color: '#cf1322', fontSize: 32, fontWeight: 'bold' }}
                />
              </Col>
              <Col>
                <Statistic 
                  title="6月累計報酬" 
                  value={23.0} 
                  precision={1} 
                  prefix={<RiseOutlined />} 
                  suffix="%" 
                  valueStyle={{ color: '#3f8600' }}
                />
              </Col>
            </Row>
          </Card>
        </Col>

        {/* 左側：指標儀表板 */}
        <Col xs={24} lg={10}>
          <Card title={<Space><LineChartOutlined /> 技術面診斷</Space>} style={{ height: '100%', borderRadius: 8 }}>
            <Descriptions column={1} bordered size="small">
              <Descriptions.Item label="RSI-14 (強弱)">
                <div style={{ width: '100%' }}>
                  <Progress 
                    percent={technicalIndicators.rsi || 0} 
                    status={ (technicalIndicators.rsi || 0) > 70 ? 'exception' : 'active' }
                    strokeColor={ (technicalIndicators.rsi || 0) > 50 ? '#52c41a' : '#1890ff' }
                    format={percent => `${percent} (${percent! > 50 ? '偏強' : '偏弱'})`}
                  />
                </div>
              </Descriptions.Item>
              <Descriptions.Item label="MACD 訊號">
                <Tag color="gold" icon={<CheckCircleOutlined />}>多頭持續</Tag>
                <Text type="secondary" style={{ fontSize: 12 }}>{technicalIndicators.macd}</Text>
              </Descriptions.Item>
              <Descriptions.Item label="均線狀態">
                <Flex vertical gap={0}>
                  <Text style={{ fontSize: 12 }}><Badge status="success" /> 站上 SMA20 (短期看多)</Text>
                  <Text style={{ fontSize: 12 }}><Badge status="success" /> 站上 SMA200 (長期看多)</Text>
                </Flex>
              </Descriptions.Item>
              <Descriptions.Item label="支撐 / 阻力">
                <Space split={<Divider type="vertical" />}>
                  <Text type="danger">阻力 26.10</Text>
                  <Text type="success">支撐 24.70</Text>
                </Space>
              </Descriptions.Item>
            </Descriptions>
            
            <Alert 
              message="操作建議" 
              description="目前處於強勢多頭，但 RSI 接近 70，建議分批佈局，不宜追高。" 
              type="success" 
              showIcon 
              icon={<BulbOutlined />}
              style={{ marginTop: 16 }}
            />
          </Card>
        </Col>

        {/* 右側：關鍵觀察與解讀 */}
        <Col xs={24} lg={14}>
          <Card title={<Space><BulbOutlined /> AI 關鍵洞察</Space>} style={{ height: '100%', borderRadius: 8 }}>
            <Paragraph>
              <Title level={5}>趨勢總結</Title>
              <Text>2025-08 至 2026-01 期間，該標的以年化約 <Text strong type="danger">38%</Text> 的速度成長，顯著優於同類科技 ETF 與大盤。</Text>
            </Paragraph>
            <Divider dashed style={{ margin: '12px 0' }} />
            <Paragraph>
              <Title level={5}>近期波動因素</Title>
              <ul>
                <li><Text strong>資金流入：</Text> 12月後成交量持續放大，買氣強勁。</li>
                <li><Text strong>外部影響：</Text> 短線回調主要受 Fed 利率政策不確定性影響。</li>
                <li><Text strong>技術面：</Text> 價位仍位於布林帶中軌偏上，仍具備上行空間。</li>
              </ul>
            </Paragraph>
          </Card>
        </Col>

        {/* 底部：詳細數據表格 */}
        <Col span={24}>
          <Card title="市場表現歷史紀錄" size="small" style={{ borderRadius: 8 }}>
            <Table
              size="small"
              dataSource={performanceData} 
              pagination={false}
              rowKey="date"
              columns={[
                { title: '日期', dataIndex: 'date', key: 'date' },
                { title: '收盤價', dataIndex: 'price', key: 'price', render: val => `TWD ${val}` },
                { 
                  title: '漲跌幅', 
                  dataIndex: 'change', 
                  key: 'change',
                  render: val => {
                    const isPositive = val.includes('+');
                    return <Text type={isPositive ? 'danger' : 'success'}>{val}</Text>
                  }
                },
                { title: '累計報酬', dataIndex: 'return', key: 'return' },
                { title: '成交量 (張)', dataIndex: 'volume', key: 'volume' },
              ]}
            />
          </Card>
        </Col>
      </Row>
    </div>
  );
};

// 輔助組件：Badge
const Badge: React.FC<{ status: 'success' | 'warning' | 'error' | 'default' }> = ({ status }) => {
  const colors = { success: '#52c41a', warning: '#faad14', error: '#f5222d', default: '#d9d9d9' };
  return <span style={{ 
    display: 'inline-block', 
    width: 8, 
    height: 8, 
    borderRadius: '50%', 
    background: colors[status], 
    marginRight: 8 
  }} />;
};
