import { Layout, Menu, Avatar, Dropdown, Space } from "antd";
import {
  DashboardOutlined,
  FundOutlined,
  LineChartOutlined,
  LogoutOutlined,
  SettingOutlined,
} from "@ant-design/icons";
import { Outlet, useNavigate } from "react-router-dom";
import { useAuthStore } from "@/stores/auth.store";
import { useUIStore } from "@/stores/ui.store";
import { logout } from "@/api/auth.api";

const { Header, Sider, Content, Footer } = Layout;

/**
 * 主應用佈局
 * - 側欄導航
 * - 頂部欄（使用者菜單）
 * - 內容區
 */
export function MainLayout() {
  const navigate = useNavigate();
  const { user, logout: logoutStore } = useAuthStore();
  const { sidebarCollapsed, toggleSidebar } = useUIStore();

  const handleLogout = async () => {
    try {
      await logout();
      logoutStore();
      navigate("/auth/login");
    } catch (error) {
      console.error("Logout failed:", error);
    }
  };

  const userMenu = [
    {
      key: "settings",
      icon: <SettingOutlined />,
      label: "設定",
      onClick: () => navigate("/settings"),
    },
    {
      type: "divider" as const,
    },
    {
      key: "logout",
      icon: <LogoutOutlined />,
      label: "登出",
      onClick: handleLogout,
    },
  ];

  const navMenu = [
    {
      key: "dashboard",
      icon: <DashboardOutlined />,
      label: "儀表板",
      onClick: () => navigate("/dashboard"),
    },
    {
      key: "portfolio",
      icon: <FundOutlined />,
      label: "投資組合",
      onClick: () => navigate("/portfolio"),
    },
    {
      key: "stocks",
      icon: <LineChartOutlined />,
      label: "股票行情",
      onClick: () => navigate("/stocks"),
    },
  ];

  return (
    <Layout style={{ minHeight: "100vh" }}>
      {/* 側欄 */}
      <Sider
        collapsible
        collapsed={sidebarCollapsed}
        onCollapse={toggleSidebar}
        width={200}
      >
        <div
          style={{
            color: "white",
            fontSize: 20,
            fontWeight: "bold",
            padding: "16px",
            textAlign: "center",
          }}
        >
          {!sidebarCollapsed ? "Stock Assistant" : "SA"}
        </div>
        <Menu theme="dark" mode="inline" items={navMenu} />
      </Sider>

      {/* 主區域 */}
      <Layout>
        {/* 頂部欄 */}
        <Header
          style={{
            background: "#fff",
            padding: "0 24px",
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            borderBottom: "1px solid #f0f0f0",
          }}
        >
          <div>Stock Assistant</div>
          <Space>
            <Dropdown menu={{ items: userMenu }}>
              <div style={{ cursor: "pointer" }}>
                <Avatar src={user?.picture_url} size="large">
                  {user?.display_name?.[0]}
                </Avatar>
              </div>
            </Dropdown>
          </Space>
        </Header>

        {/* 內容區 */}
        <Content style={{ margin: "24px 16px", padding: 24 }}>
          <Outlet />
        </Content>

        {/* 頁尾 */}
        <Footer style={{ textAlign: "center" }}>
          Stock Assistant ©2026 Created with React + Spring Boot
        </Footer>
      </Layout>
    </Layout>
  );
}
