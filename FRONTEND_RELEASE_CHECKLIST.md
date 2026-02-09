# 🚀 前端上線審核清單（2026-02-09）

## 📊 快速概覽
- **現有評分**：6.2/10
- **修復後評分**：7.5/10（修復所有 P0 後可上線）
- **上線缺口**：4 個 BLOCKER + 6 個高優先級問題
- **預計修復時間**：8-12 小時

---

## 🔴 BLOCKER - 必須修復才能上線

### #1 缺少 Lazy Loading（性能風險）
📍 [src/router/routes.tsx](src/router/routes.tsx)

**問題**：所有 9 個頁面都在首個 bundle 中，沒有按路由分割
- 首屏加載體積大（預估 500KB+ gzip）
- 初始 TTI 變慢

**修復方案**：
```typescript
import { lazy, Suspense } from 'react';

const Dashboard = lazy(() => import("@/pages/Dashboard"));
const Portfolio = lazy(() => import("@/pages/Portfolio"));
// ... 其他頁面

// 在 routeConfig 中包裝
<Suspense fallback={<Spin />}>
  <Dashboard />
</Suspense>
```

**驗證**：
```bash
npm install -D rollup-plugin-visualizer
ANALYZE=true npm run build
# 檢查 dist/stats.html，應該看到多個 chunk（每個 ~50-100KB）
```

**工作量**：M（1-2 小時）

---

### #2 缺少全域錯誤捕捉（監控風險）
📍 多個 store/page 檔案

**問題**：
- 異步錯誤、Promise rejection 無法被捕捉
- 生產環境 bug 無人監控

**修復方案**：
```typescript
// src/utils/setupGlobalErrorHandlers.ts
window.addEventListener('unhandledrejection', (event) => {
  console.error('Unhandled rejection:', event.reason);
  Sentry.captureException(event.reason);
});

// src/main.tsx
setupGlobalErrorHandlers();
```

**工作量**：S（20 分鐘）

---

### #3 React Query 缺少 staleTime（API 濫用風險）
📍 [src/app/AppProviders.tsx](src/app/AppProviders.tsx#L10-L15)

**問題**：只設 gcTime，沒設 staleTime → 頻繁 fetch（每頁面切換都 refetch）

**修復方案**：
```typescript
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 3 * 60 * 1000, // 3 分鐘
      gcTime: 10 * 60 * 1000,
    },
  },
});
```

**工作量**：S（10 分鐘）

---

### #4 console.error 未上報 Sentry（>30 處）
📍 stores/、pages/、components/ 等多個檔案

**問題**：所有 console.error 只印控制台，Sentry 看不到 → 無法統計 bug

**修復方案**：
```typescript
// src/utils/logger.ts
export const logger = {
  error: (msg: string, err?: unknown, ctx?: Record<string, unknown>) => {
    console.error(msg, err);
    Sentry.captureException(err || new Error(msg), { 
      tags: { category: 'app_error' },
      contexts: { custom: ctx }
    });
  }
};

// 全局搜尋「console.error」，改用 logger.error()
```

**工作量**：L（2-3 小時，包括修改所有 console.error 處）

---

## 🟠 高優先級（建議上線前完成）

### #5 Sentry DSN 硬編碼（安全/配置管理風險）
📍 [src/main.tsx#L8](src/main.tsx#L8)

**問題**：DSN 在源代碼中，無法按環境切換 dev/staging/prod

**修復**：
```typescript
// .env.local
VITE_SENTRY_DSN=https://...dev...@sentry.io/...
VITE_SENTRY_ENVIRONMENT=development

// src/main.tsx
Sentry.init({
  dsn: import.meta.env.VITE_SENTRY_DSN,
  environment: import.meta.env.VITE_SENTRY_ENVIRONMENT,
  // ...
});
```

**工作量**：S（5 分鐘）

---

### #6 缺少版本追蹤（可追溯性風險）
📍 整個應用

**問題**：無法追溯「這個 bug 是哪個版本引入的」

**修復**：
```bash
# scripts/generate-version.js（在 prebuild 時執行）
# 自動生成 src/version.ts 包含 git commit + 時間戳

# src/main.tsx
import { VERSION } from './version';
Sentry.init({
  release: `frontend@${VERSION.version}+${VERSION.commit}`,
});
```

**工作量**：M（1-2 小時）

---

### #7 缺少自動重試機制（低網速容忍度）
📍 [src/utils/http.ts](src/utils/http.ts)

**問題**：網路波動（timeout/DNS 失敗）無法自動重試 → 使用者需手動重新整理

**修復**：
```typescript
// 在 HTTP 攔截器中添加 exponential backoff
const MAX_RETRIES = 3;
const shouldRetry = config.retryCount < MAX_RETRIES && 
  (retryableStatusCodes.includes(status) || isNetworkError);

if (shouldRetry) {
  const delayMs = Math.pow(2, retryCount) * 1000;
  await sleep(delayMs);
  return instance(config);
}
```

**工作量**：M（2-3 小時）

---

### #8 所有 `as any` 型別斷言（類型安全風險）
📍 [src/pages/Trades/index.tsx#L41](src/pages/Trades/index.tsx#L41)、[src/utils/http.ts#L20](src/utils/http.ts#L20) 等

**問題**：繞過 TypeScript 類型檢查 → 無法在編譯時捕捉 bug

**修復**：逐個替換為明確的類型定義

**工作量**：M（1-2 小時）

---

## 🟡 中優先級（V1.1 可後補）

- [ ] Web Vitals 測試蒐集（S，15 分鐘）
- [ ] Bundle 分析工具整合（S，20 分鐘）
- [ ] 離線狀態提示（S，10 分鐘）

---

## ✅ 現有完成項

- ✅ HTTP 攔截器（已實現）
- ✅ Sentry 初始化（已實現）
- ✅ ErrorBoundary（已實現）
- ✅ import.store.pollingIntervalId 清理（已實現）
- ✅ 大部分 P1 問題修復（已實現）

---

## 📋 上線檢查清單

### 發佈前必做（共 15 項）

#### 代碼品質
- [ ] 所有 123 個 console.error 改為 logger.error()
- [ ] 移除 8 個 `as any` 型別斷言
- [ ] ESLint 無違規 (`npm run lint`)

#### 性能
- [ ] Lazy Loading 實現（4 個 chunk，首頁 < 200KB gzip）
- [ ] Bundle 分析檢查（已識別瓶頸）
- [ ] React Query staleTime 配置完成

#### 監控
- [ ] Sentry DSN 改環境變數
- [ ] 全域錯誤捕捉完成
- [ ] 版本追蹤自動生成配置完成
- [ ] Web Vitals 蒐集啟用

#### 可靠性
- [ ] 自動重試機制測試完成
- [ ] 多工況 API 失敗測試（超時、DNS、斷網）
- [ ] Browser DevTools 無 error/warning

#### 部署
- [ ] `.env.production` 配置完整
- [ ] 部署前最後一次打包驗證
- [ ] Sentry release 標籤正確

---

## 🚀 2 日修復計畫

### 第 1 天（4-5 小時）
1. 全域錯誤捕捉（20 min）
2. React Query staleTime（10 min）
3. Lazy Loading 實現（1-2 h）
4. console.error → logger（1-2 h）
5. Sentry DSN env（5 min）

### 第 2 天（4-5 小時）
1. 自動重試機制（2-3 h）
2. 版本追蹤配置（1-2 h）
3. 移除 `as any`（20-30 min）
4. 打包 & 測試（1 h）

**合計**：8-10 小時（1.5-2 天）

---

## 📞 若實現困難，請補充

1. **CI/CD 現況**
   - build 時是否已計算 git commit?
   - 環境變數如何儲存（.env vs 部署平台）?

2. **監控現況**  
   - Sentry 是否已建多個 project?
   - 是否需要 release tracking?

3. **性能目標**
   - Acceptable 首屏 TTI 是多少?
   - Bundle size 目標 (gzip 後)?

---

**評分提升預期**：6.2/10 → 7.5/10（4 個 BLOCKER 修復後）→ 8.2/10（全部 10 項修復後）

**上線建議**：修復完 H1 的 4 個 BLOCKER + H2 的 3 個高優先級 = 7 項修復 → 可上線（評分 ≥ 7.0）

