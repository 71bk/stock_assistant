import { useEffect, useRef } from 'react';
import { useLocation } from 'react-router-dom';
import { analyticsApi } from '../../api/analytics.api';
import { logger } from '../../utils/logger';

const SESSION_KEY = 'analytics_session_id';

function createUuid(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (character) => {
    const random = Math.floor(Math.random() * 16);
    const value = character === 'x' ? random : (random & 0x3) | 0x8;
    return value.toString(16);
  });
}

function getSessionId(): string {
  try {
    const existing = window.sessionStorage.getItem(SESSION_KEY);
    if (existing) {
      return existing;
    }
    const created = createUuid();
    window.sessionStorage.setItem(SESSION_KEY, created);
    return created;
  } catch {
    return createUuid();
  }
}

export function AnalyticsPageTracker() {
  const location = useLocation();
  const lastTrackedRoute = useRef<string | null>(null);

  useEffect(() => {
    const route = location.pathname;
    if (lastTrackedRoute.current === route) {
      return;
    }
    lastTrackedRoute.current = route;

    analyticsApi.trackPageView({
      eventId: createUuid(),
      sessionId: getSessionId(),
      eventType: 'PAGE_VIEW',
      route,
      occurredAt: new Date().toISOString(),
    }).catch((error) => {
      logger.info('Page analytics tracking failed', {
        message: error instanceof Error ? error.message : String(error),
      });
    });
  }, [location.pathname]);

  return null;
}
