import { Result, Button } from "antd";
import { useNavigate } from "react-router-dom";

interface ErrorStateProps {
  status?: "404" | "500" | "403";
  title?: string;
  message?: string;
  showHome?: boolean;
}

/**
 * 錯誤狀態展示
 */
export function ErrorState({
  status = "500",
  title,
  message,
  showHome = true,
}: ErrorStateProps) {
  const navigate = useNavigate();

  const statusConfig: Record<string, { title: string; subTitle: string }> = {
    "404": {
      title: "404",
      subTitle: "頁面不存在",
    },
    "500": {
      title: "500",
      subTitle: "伺服器錯誤",
    },
    "403": {
      title: "403",
      subTitle: "無權限存取",
    },
  };

  const config = statusConfig[status];

  return (
    <div style={{ minHeight: "100vh", display: "flex", alignItems: "center" }}>
      <Result
        status={status}
        title={title || config.title}
        subTitle={message || config.subTitle}
        extra={
          showHome && (
            <Button type="primary" onClick={() => navigate("/")}>
              返回首頁
            </Button>
          )
        }
      />
    </div>
  );
}
