import React from 'react';
import * as Sentry from "@sentry/react";
import ReactDOM from 'react-dom/client';
import App from './app/App';
import { setupGlobalErrorHandlers } from './utils/setupGlobalErrorHandlers';
import { VERSION } from './version';
import './styles/global.css';

setupGlobalErrorHandlers();

Sentry.init({
  dsn: import.meta.env.VITE_SENTRY_DSN,
  release: `frontend@${VERSION.version}+${VERSION.commit}`,
  // Setting this option to true will send default PII data to Sentry.
  // For example, automatic IP address collection on events
  sendDefaultPii: true,
  environment: import.meta.env.VITE_SENTRY_ENVIRONMENT || "dev",

  // Performance Monitoring
  tracesSampleRate: import.meta.env.PROD ? 0.1 : 1.0,

  // Session Replay
  replaysSessionSampleRate: 0.1,
  replaysOnErrorSampleRate: 1.0,

  integrations: [
    Sentry.browserTracingIntegration(),
    Sentry.replayIntegration(),
  ],
});


ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);