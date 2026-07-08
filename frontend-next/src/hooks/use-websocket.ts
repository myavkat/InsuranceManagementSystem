"use client";

import { useEffect, useRef, useCallback } from "react";
import { useAuthStore } from "@/lib/store/auth-store";
import { useNotificationStore } from "@/lib/store/notification-store";
import type { NotificationType } from "@/lib/notification-types";
import { toast } from "sonner";

// The WebSocket endpoint on the API Gateway
// NOTE: This URL will need to be updated when the backend WebSocket endpoint is available.
// For now, use a configurable URL that can be set via environment variable.
const WS_URL =
  process.env.NEXT_PUBLIC_WS_URL || "ws://localhost:8080/ws/notifications";

// Reconnection config
const MAX_RECONNECT_DELAY = 30_000; // 30 seconds
const INITIAL_RECONNECT_DELAY = 1_000; // 1 second

// Expected message format from the server
interface ServerNotification {
  type: NotificationType;
  title: string;
  message: string;
  data?: {
    entityType?: string;
    entityId?: string;
  };
}

/**
 * Custom hook for WebSocket notifications.
 *
 * Connects to the notification WebSocket endpoint with JWT auth,
 * handles reconnection with exponential backoff, and dispatches
 * incoming messages to the notification store and toast system.
 *
 * Call this ONCE in a top-level provider or layout component.
 */
export function useWebSocket({
  onNavigate,
}: {
  onNavigate?: (path: string) => void;
} = {}) {
  const accessToken = useAuthStore((s) => s.accessToken);
  const addNotification = useNotificationStore((s) => s.addNotification);
  const setConnected = useNotificationStore((s) => s.setConnected);

  const wsRef = useRef<WebSocket | null>(null);
  const reconnectDelayRef = useRef(INITIAL_RECONNECT_DELAY);
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const mountedRef = useRef(true);
  const accessTokenRef = useRef(accessToken);
  // Always keep the ref in sync with the latest token
  accessTokenRef.current = accessToken;

  const connect = useCallback(() => {
    if (!accessToken) return; // Don't connect without auth

    // Clean up existing connection
    if (wsRef.current) {
      wsRef.current.close();
      wsRef.current = null;
    }

    // Build URL with auth token as query param
    // Use accessTokenRef.current to avoid stale closures in reconnect timers.
    // The non-null assertion is safe because the guard above ensures accessToken is truthy.
    const url = `${WS_URL}?token=${encodeURIComponent(accessTokenRef.current!)}`;

    try {
      const ws = new WebSocket(url);
      wsRef.current = ws;

      ws.onopen = () => {
        if (!mountedRef.current) return;
        setConnected(true);
        reconnectDelayRef.current = INITIAL_RECONNECT_DELAY; // Reset on successful connection
      };

      ws.onmessage = (event) => {
        if (!mountedRef.current) return;

        try {
          const data: ServerNotification = JSON.parse(event.data);

          // Add to persistent notification store
          addNotification(data);

          // Show toast for transient notification
          switch (data.type) {
            case "estimation_status":
              toast(data.title, {
                description: data.message,
                action: data.data?.entityId
                  ? {
                      label: "View",
                      onClick: () => {
                        const entityId = data.data?.entityId as string;
                        onNavigate?.(`/estimations/${entityId}`);
                      },
                    }
                  : undefined,
              });
              break;
            case "validation_failure":
              toast.error(data.title, {
                description: data.message,
                duration: 6000, // Keep error toasts longer
              });
              break;
            case "system_alert":
              toast(data.title, {
                description: data.message,
                duration: 5000,
              });
              break;
            default:
              toast(data.title, { description: data.message });
          }
        } catch {
          // Ignore malformed messages — don't crash the WebSocket handler
          console.warn("Received malformed WebSocket message:", event.data);
        }
      };

      ws.onerror = () => {
        // The onclose handler will fire after this and handle reconnection
        // Don't call setConnected(false) here — wait for onclose
      };

      ws.onclose = (event) => {
        if (!mountedRef.current) return;
        setConnected(false);
        wsRef.current = null;

        // Only reconnect on abnormal closure (not intentional close)
        // event.code 1000 = normal closure, 1001 = going away
        if (event.code !== 1000) {
          const delay = reconnectDelayRef.current;
          reconnectTimerRef.current = setTimeout(() => {
            if (mountedRef.current) {
              connect();
            }
          }, delay);

          // Exponential backoff: double the delay, cap at MAX
          reconnectDelayRef.current = Math.min(
            delay * 2,
            MAX_RECONNECT_DELAY,
          );
        }
      };
    } catch {
      // WebSocket constructor can throw (e.g., invalid URL)
      if (mountedRef.current) {
        const delay = reconnectDelayRef.current;
        reconnectTimerRef.current = setTimeout(() => {
          if (mountedRef.current) {
            connect();
          }
        }, delay);
        reconnectDelayRef.current = Math.min(delay * 2, MAX_RECONNECT_DELAY);
      }
    }
  }, [accessToken, addNotification, setConnected]);

  // Connect when token changes, disconnect on unmount
  useEffect(() => {
    mountedRef.current = true;

    if (accessToken) {
      connect();
    }

    return () => {
      mountedRef.current = false;

      // Clear reconnect timer
      if (reconnectTimerRef.current) {
        clearTimeout(reconnectTimerRef.current);
        reconnectTimerRef.current = null;
      }

      // Close WebSocket cleanly
      if (wsRef.current) {
        wsRef.current.close(1000, "Component unmounted");
        wsRef.current = null;
      }

      setConnected(false);
    };
  }, [accessToken, connect, setConnected]);
}
