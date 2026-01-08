import { Layout } from "antd";
import { Outlet } from "react-router-dom";

const { Content, Footer } = Layout;

/**
 * 認證頁面佈局（登入、註冊等）
 * 簡單居中布局
 */
export function AuthLayout() {
  return (
    <Layout style={{ minHeight: "100vh" }}>
      <Content
        style={{
          display: "flex",
          justifyContent: "center",
          alignItems: "center",
        }}
      >
        <Outlet />
      </Content>
      <Footer style={{ textAlign: "center" }}>
        Stock Assistant ©2026
      </Footer>
    </Layout>
  );
}
