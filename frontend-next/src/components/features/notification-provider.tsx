"use client";

import { useWebSocket } from "@/hooks/use-websocket";

/**
 * Provider that initializes the WebSocket connection for notifications.
 * Mount this ONCE inside the root Providers component.
 *
 * This is a Client Component — it uses hooks internally.
 */
export function NotificationProvider({
  children,
}: {
  children: React.ReactNode;
}) {
  // Initialize WebSocket connection
  useWebSocket();

  // Render children as-is — this component just establishes the side effect
  return <>{children}</>;
}
