import React from 'react';
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
  HomeOutlined
} from '@ant-design/icons';
import { useAuthStore } from '../../stores/auth.store';
import { useUIStore } from '../../stores/ui.store';

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
        label: 'Settings',
        icon: <SettingOutlined />,
        onClick: () => navigate('/settings'),
      },
      {
        key: 'logout',
        label: 'Logout',
        icon: <LogoutOutlined />,
        onClick: () => logout(),
      },
    ],
  };

  // Generate breadcrumb items based on path
  const breadcrumbItems = location.pathname
    .split('/')
    .filter((i) => i)
    .map((i) => ({ title: i.charAt(0).toUpperCase() + i.slice(1) }));

  // Determine selected key logic
  const getSelectedKey = () => {
      // Default match
      const key = location.pathname;
      if (key === '/') return '/'; 
      // Handle sub-routes if necessary
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
              key: '/',
              icon: <HomeOutlined />,
              label: 'Home',
            },
            {
              key: '/dashboard',
              icon: <PieChartOutlined />,
              label: 'Dashboard',
            },
            {
              key: '/portfolio',
              icon: <RiseOutlined />,
              label: 'Portfolio',
            },
            {
              key: '/stocks',
              icon: <FileTextOutlined />,
              label: 'Stocks',
            },
            {
              key: '/import',
              icon: <UploadOutlined />,
              label: 'Import',
            },
            // {
            //   key: '/reports',
            //   icon: <FileTextOutlined />,
            //   label: 'Reports',
            // },
            // {
            //   key: '/settings',
            //   icon: <SettingOutlined />,
            //   label: 'Settings',
            // },
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
            <Breadcrumb items={[{ title: 'App' }, ...breadcrumbItems]} style={{ marginLeft: 16 }} />
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
    </Layout>
  );
};
