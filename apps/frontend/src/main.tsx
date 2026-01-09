import React from 'react';
import * as Sentry from "@sentry/react";
import ReactDOM from 'react-dom/client';
import App from './app/App';
import './styles/global.css';

Sentry.init({
  dsn: import.meta.env.VITE_SENTRY_DSN,
  environment: import.meta.env.VITE_SENTRY_ENVIRONMENT || "dev",

  // Performance Monitoring
  tracesSampleRate: import.meta.env.PROD ? 0.1 : 1.0,

  // Session Replay (optional but recommended)
  replaysSessionSampleRate: 0.1, // 10% of sessions
  replaysOnErrorSampleRate: 1.0, // 100% when error occurs

  integrations: [
    Sentry.browserTracingIntegration(),
    Sentry.replayIntegration(),
  ],

  // Filter out sensitive data
  beforeSend(event) {
    // Remove sensitive information if needed
    if (event.request) {
      delete event.request.cookies;
    }
    return event;
  },
});


ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);