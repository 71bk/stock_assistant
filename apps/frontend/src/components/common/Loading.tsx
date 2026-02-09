import { Spin } from "antd";

interface LoadingProps {
  tip?: string;
  fullscreen?: boolean;
}

/**
 * 加載中指示器
 */
import { Typography, Space } from "antd";

export function Loading({ tip = "加載中...", fullscreen = false }: LoadingProps) {
  const content = (
    <Space direction="vertical" align="center">
      <Spin size="large" />
      <Typography.Text type="secondary">{tip}</Typography.Text>
    </Space>
  );

  if (fullscreen) {
    return (
      <div
        style={{
          display: "flex",
          justifyContent: "center",
          alignItems: "center",
          minHeight: "100vh",
        }}
      >
        {content}
      </div>
    );
  }

  return (
    <div
      style={{
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        minHeight: 300,
      }}
    >
      {content}
    </div>
  );
}
