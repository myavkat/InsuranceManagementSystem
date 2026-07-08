"use client";

import { useRouter } from "next/navigation";
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
  const router = useRouter();

  // Initialize WebSocket connection with client-side navigation support
  useWebSocket({ onNavigate: (path) => router.push(path) });

  // Render children as-is — this component just establishes the side effect
  return <>{children}</>;
}
