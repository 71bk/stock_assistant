import React, { useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Spin, Typography } from 'antd';
import { useAuthStore } from '../../stores/auth.store';

const { Text } = Typography;

const AuthCallback: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { checkAuth } = useAuthStore();

  useEffect(() => {
    const handleCallback = async () => {
      // 1. Check for errors in URL (e.g. ?error=access_denied)
      const error = searchParams.get('error');
      if (error) {
        console.error('Auth error:', error);
        navigate('/login?error=' + error, { replace: true });
        return;
      }

      // 2. Trigger store to fetch user profile (validate cookie)
      // We assume backend has already set the HttpOnly cookie before redirecting here
      await checkAuth();

      // 3. Navigate to dashboard (or original destination if we persisted it)
      navigate('/dashboard', { replace: true });
    };

    handleCallback();
  }, [checkAuth, navigate, searchParams]);

  return (
    <div style={{ 
      display: 'flex', 
      flexDirection: 'column',
      justifyContent: 'center', 
      alignItems: 'center', 
      height: '100vh', 
      background: '#f0f2f5' 
    }}>
      <Spin size="large" />
      <Text type="secondary" style={{ marginTop: 16 }}>Verifying authentication...</Text>
    </div>
  );
};

export default AuthCallback;
