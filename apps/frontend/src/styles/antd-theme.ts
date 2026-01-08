import { ThemeConfig } from "antd";

/**
 * AntD Theme 主題配置
 * 定義全域設計 token
 */
export const antdTheme: ThemeConfig = {
  token: {
    // 顏色
    colorPrimary: "#1890ff",
    colorSuccess: "#52c41a",
    colorWarning: "#faad14",
    colorError: "#ff4d4f",
    colorInfo: "#1890ff",
    colorTextBase: "#000000",
    colorBgBase: "#ffffff",

    // 尺寸
    borderRadius: 4,
    fontSize: 14,
    fontSizeHeading1: 32,
    fontSizeHeading2: 28,
    fontSizeHeading3: 24,

    // 間距
    margin: 16,
    marginXS: 8,
    marginSM: 12,
    marginMD: 16,
    marginLG: 24,
    marginXL: 32,

    padding: 16,
    paddingXS: 8,
    paddingSM: 12,
    paddingMD: 16,
    paddingLG: 24,
    paddingXL: 32,
  },
  components: {
    // Button
    Button: {
      primaryColor: "#1890ff",
    },
    // Table
    Table: {
      headerBg: "#fafafa",
      headerBorderRadius: 4,
    },
    // Layout
    Layout: {
      headerBg: "#fff",
      headerHeight: 64,
      headerPadding: "0 24px",
      siderBg: "#001529",
    },
  },
};
