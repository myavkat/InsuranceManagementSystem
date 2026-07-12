/**
 * Notification type constants used across the app.
 * These match the expected event types from the backend WebSocket endpoint.
 */
export const NOTIFICATION_TYPES = {
  ESTIMATION_STATUS: "estimation_status",
  VALIDATION_FAILURE: "validation_failure",
  SYSTEM_ALERT: "system_alert",
} as const;

export type NotificationType =
  (typeof NOTIFICATION_TYPES)[keyof typeof NOTIFICATION_TYPES];
