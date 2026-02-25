import { Card, Typography, Button } from 'antd';
import { GoogleOutlined } from '@ant-design/icons';
import { GOOGLE_LOGIN_URL } from '../../api/auth.api';

const { Title, Text } = Typography;

export function Login() {
  const handleLogin = () => {
    // Redirect to backend OAuth endpoint
    window.location.href = GOOGLE_LOGIN_URL;
  };

  return (
    <Card style={{ width: 400, textAlign: 'center', borderRadius: 8, boxShadow: '0 4px 12px rgba(0,0,0,0.1)' }}>
      <div style={{ marginBottom: 24 }}>
        {/* Logo Placeholder */}
        <div style={{ width: 64, height: 64, background: '#1677ff', borderRadius: '50%', margin: '0 auto 16px', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff', fontSize: 24, fontWeight: 'bold' }}>SA</div>
        <Title level={3}>Welcome Back</Title>
        <Text type="secondary">Sign in to manage your stock portfolio</Text>
      </div>
      
      <Button 
        type="primary" 
        icon={<GoogleOutlined />} 
        size="large" 
        block 
        onClick={handleLogin}
      >
        Sign in with Google
      </Button>
    </Card>
  );
}

export function OAuthCallback() {
  return (
    <div style={{ textAlign: "center" }}>
      <h1>處理中...</h1>
      <p>正在完成登入流程</p>
    </div>
  );
}
