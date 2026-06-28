import React, { useState, useEffect } from 'react';
import { Layout, Menu, Button, Dropdown, Avatar, theme, Drawer, Grid, Flex, Switch, Typography } from 'antd';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import {
  DashboardOutlined,
  BarChartOutlined,
  RobotOutlined,
  ApiOutlined,
  ToolOutlined,
  MenuUnfoldOutlined,
  MenuFoldOutlined,
  UserOutlined,
  LogoutOutlined,
  RollbackOutlined,
  SunOutlined,
  MoonOutlined,
} from '@ant-design/icons';
import { useAuthStore } from '../../stores/auth.store';
import { useUIStore } from '../../stores/ui.store';

const { Header, Sider, Content } = Layout;
const { Text } = Typography;

/**
 * Dedicated layout for the admin console (`/admin/*`).
 * Kept fully separate from the user-facing MainLayout so the admin area
 * doesn't share the user navigation/sidebar.
 */
export const AdminLayout: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { user, logout } = useAuthStore();
  const { siderCollapsed, toggleSider, theme: uiTheme, setTheme } = useUIStore();
  const {
    token: { colorBgContainer, borderRadiusLG, colorBorderSecondary },
  } = theme.useToken();

  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;
  const [drawerOpen, setDrawerOpen] = useState(false);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setDrawerOpen(false);
  }, [location.pathname]);

  const handleMenuClick = ({ key }: { key: string }) => {
    navigate(key);
    if (isMobile) {
      setDrawerOpen(false);
    }
  };

  const userMenu = {
    items: [
      {
        key: 'back-to-app',
        label: '返回 App',
        icon: <RollbackOutlined />,
        onClick: () => navigate('/dashboard'),
      },
      {
        key: 'logout',
        label: '登出',
        icon: <LogoutOutlined />,
        onClick: () => logout(),
      },
    ],
  };

  const menuItems = [
    { key: '/admin/overview', icon: <DashboardOutlined />, label: '總覽' },
    { key: '/admin/users', icon: <BarChartOutlined />, label: '使用者分析' },
    { key: '/admin/ai-usage', icon: <RobotOutlined />, label: 'AI 用量' },
    { key: '/admin/api-traffic', icon: <ApiOutlined />, label: 'API 流量' },
    { key: '/admin/maintenance', icon: <ToolOutlined />, label: '維護工具' },
  ];

  const getSelectedKey = () => {
    const match = menuItems.find((item) => location.pathname.startsWith(item.key));
    return match ? match.key : '/admin/overview';
  };

  const MenuContent = (
    <Menu
      theme={uiTheme}
      mode="inline"
      selectedKeys={[getSelectedKey()]}
      onClick={handleMenuClick}
      items={menuItems}
      style={{ borderRight: 0 }}
    />
  );

  return (
    <Layout style={{ minHeight: '100vh' }}>
      {!isMobile && (
        <Sider trigger={null} collapsible collapsed={siderCollapsed} theme={uiTheme} style={{ borderRight: `1px solid ${colorBorderSecondary}` }}>
          <div className="demo-logo-vertical" style={{ height: 64, display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 'bold', fontSize: 18, color: '#1677ff' }}>
            {siderCollapsed ? 'AD' : '管理後台'}
          </div>
          {MenuContent}
        </Sider>
      )}

      {isMobile && (
        <Drawer
          title="管理後台"
          placement="left"
          onClose={() => setDrawerOpen(false)}
          open={drawerOpen}
          styles={{
            body: { padding: 0 },
            wrapper: { width: 240 },
          }}
        >
          {MenuContent}
        </Drawer>
      )}

      <Layout>
        <Header style={{ padding: '0 16px', background: colorBgContainer, display: 'flex', alignItems: 'center', justifyContent: 'space-between', borderBottom: `1px solid ${colorBorderSecondary}` }}>
          <Flex align="center">
            <Button
              type="text"
              icon={isMobile ? <MenuUnfoldOutlined /> : (siderCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />)}
              onClick={() => isMobile ? setDrawerOpen(true) : toggleSider()}
              style={{ fontSize: '16px', width: 64, height: 64 }}
            />
            <Text strong style={{ marginLeft: 8 }}>管理後台</Text>
          </Flex>
          <Flex align="center" gap={16}>
            <Button type="text" icon={<RollbackOutlined />} onClick={() => navigate('/dashboard')}>
              {!isMobile && '返回 App'}
            </Button>
            <Switch
              checked={uiTheme === 'dark'}
              onChange={(checked) => setTheme(checked ? 'dark' : 'light')}
              checkedChildren={<MoonOutlined />}
              unCheckedChildren={<SunOutlined />}
              aria-label="切換深色／亮色模式"
            />
            <Dropdown menu={userMenu} placement="bottomRight">
              <div style={{ cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 8, padding: '4px 12px', borderRadius: 6, transition: 'background 0.3s' }} className="user-dropdown-trigger">
                <Avatar src={user?.pictureUrl} icon={<UserOutlined />} style={{ backgroundColor: '#1677ff', flexShrink: 0 }} />
                {!isMobile && (
                  <span style={{ fontWeight: 500, maxWidth: 160, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{user?.displayName || 'Admin'}</span>
                )}
              </div>
            </Dropdown>
          </Flex>
        </Header>
        <Content
          style={{
            margin: '24px 16px',
            padding: 24,
            minHeight: 280,
            background: colorBgContainer,
            borderRadius: borderRadiusLG,
            display: 'flex',
            flexDirection: 'column',
            overflow: 'hidden',
            position: 'relative',
          }}
        >
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
};

export default AdminLayout;
