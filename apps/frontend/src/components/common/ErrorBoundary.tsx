import { Component, type ErrorInfo, type ReactNode } from 'react';
import { Result, Button } from 'antd';

interface Props {
  children?: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

export class ErrorBoundary extends Component<Props, State> {
  public state: State = {
    hasError: false,
    error: null,
  };

  public static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  public componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error('Uncaught error:', error, errorInfo);
  }

  public render() {
    if (this.state.hasError) {
      return (
        <div style={{ padding: '40px', textAlign: 'center' }}>
          <Result
            status="error"
            title="應用程式發生錯誤"
            subTitle={this.state.error?.message || '發生了未預期的錯誤，請重新整理頁面。'}
            extra={[
              <Button type="primary" key="refresh" onClick={() => window.location.reload()}>
                重新整理頁面
              </Button>,
              <Button key="home" onClick={() => window.location.href = '/'}>
                回首頁
              </Button>,
            ]}
          />
        </div>
      );
    }

    return this.props.children;
  }
}
