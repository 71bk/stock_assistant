import * as Sentry from "@sentry/react";

export const setupGlobalErrorHandlers = () => {
  window.addEventListener("unhandledrejection", (event) => {
    console.error("Unhandled rejection:", event.reason);
    Sentry.captureException(event.reason);
  });

  window.addEventListener("error", (event) => {
    console.error("Global error:", event.error);
    Sentry.captureException(event.error);
  });
};
