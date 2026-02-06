import React, { useMemo } from 'react';
import { Layout, Menu, Button, Dropdown, Avatar, theme, Breadcrumb } from 'antd';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import {
  PieChartOutlined,
  RiseOutlined,
  FileTextOutlined,
  UploadOutlined,
  SettingOutlined,
  MenuUnfoldOutlined,
  MenuFoldOutlined,
  UserOutlined,
  LogoutOutlined,
  RobotOutlined,
} from '@ant-design/icons';
import { useAuthStore } from '../../stores/auth.store';
import { useUIStore } from '../../stores/ui.store';
import { FloatingAiAssistant } from '../ai/FloatingAiAssistant';

const { Header, Sider, Content } = Layout;

export const MainLayout: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { user, logout } = useAuthStore();
  const { siderCollapsed, toggleSider } = useUIStore();
  const {
    token: { colorBgContainer, borderRadiusLG },
  } = theme.useToken();

  const handleMenuClick = ({ key }: { key: string }) => {
    navigate(key);
  };

  const userMenu = {
    items: [
      {
        key: 'settings',
        label: '設定',
        icon: <SettingOutlined />,
        onClick: () => navigate('/settings'),
      },
      {
        key: 'logout',
        label: '登出',
        icon: <LogoutOutlined />,
        onClick: () => logout(),
      },
    ],
  };

  // Generate breadcrumb items based on path
  const breadcrumbItems = useMemo(() => {
    const paths = location.pathname.split('/').filter(Boolean);
    if (paths.length === 0) {
      return [{ title: '總覽' }];
    }
    return paths.map((path) => {
      const labelMap: Record<string, string> = {
        dashboard: '總覽',
        portfolio: '投資組合',
        trades: '交易紀錄',
        stocks: '股票行情',
        import: '匯入交易',
        chat: 'AI 助理',
        reports: '分析報告',
        settings: '設定',
      };
      return { title: labelMap[path] || path.charAt(0).toUpperCase() + path.slice(1) };
    });
  }, [location.pathname]);

  // Determine selected key logic
  const getSelectedKey = () => {
      const key = location.pathname;
      if (key === '/' || key === '') return '/dashboard';
      return key;
  };

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider trigger={null} collapsible collapsed={siderCollapsed} theme="light" style={{ borderRight: '1px solid #f0f0f0' }}>
        <div className="demo-logo-vertical" style={{ height: 64, display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 'bold', fontSize: 18, color: '#1677ff' }}>
           {siderCollapsed ? 'SA' : 'Invest Assistant'}
        </div>
        <Menu
          theme="light"
          mode="inline"
          selectedKeys={[getSelectedKey()]}
          onClick={handleMenuClick}
          items={[
            {
              key: '/dashboard',
              icon: <PieChartOutlined />,
              label: '總覽',
            },
            {
              key: '/portfolio',
              icon: <RiseOutlined />,
              label: '投資組合',
            },
            {
              key: '/trades',
              icon: <FileTextOutlined />,
              label: '交易紀錄',
            },
            {
              key: '/stocks',
              icon: <FileTextOutlined />,
              label: '股票行情',
            },
            {
              key: '/import',
              icon: <UploadOutlined />,
              label: '匯入交易',
            },
            {
              key: '/chat',
              icon: <RobotOutlined />,
              label: 'AI 助理',
            },
            {
              key: '/reports',
              icon: <FileTextOutlined />,
              label: '分析報告',
            },
            {
              key: '/settings',
              icon: <SettingOutlined />,
              label: '設定',
            },
          ]}
        />
      </Sider>
      <Layout>
        <Header style={{ padding: '0 16px', background: colorBgContainer, display: 'flex', alignItems: 'center', justifyContent: 'space-between', borderBottom: '1px solid #f0f0f0' }}>
          <div style={{ display: 'flex', alignItems: 'center' }}>
            <Button
              type="text"
              icon={siderCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
              onClick={toggleSider}
              style={{
                fontSize: '16px',
                width: 64,
                height: 64,
              }}
            />
            <Breadcrumb items={[{ title: '首頁' }, ...breadcrumbItems]} style={{ marginLeft: 16 }} />
          </div>
          <Dropdown menu={userMenu} placement="bottomRight">
            <div style={{ cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 8, padding: '4px 12px', borderRadius: 6, transition: 'background 0.3s' }} className="user-dropdown-trigger">
               <Avatar src={user?.pictureUrl} icon={<UserOutlined />} style={{ backgroundColor: '#1677ff' }} />
               <span style={{ fontWeight: 500 }}>{user?.displayName || 'User'}</span>
            </div>
          </Dropdown>
        </Header>
        <Content
          style={{
            margin: '24px 16px',
            padding: 24,
            minHeight: 280,
            background: colorBgContainer,
            borderRadius: borderRadiusLG,
            overflow: 'auto' 
          }}
        >
          <Outlet />
        </Content>
      </Layout>
      <FloatingAiAssistant />
    </Layout>
  );
};
