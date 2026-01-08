/**
 * 認證相關頁面
 */

export function Login() {
  return (
    <div style={{ textAlign: "center" }}>
      <h1>登入</h1>
      <p>使用 Google 帳號登入</p>
    </div>
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
