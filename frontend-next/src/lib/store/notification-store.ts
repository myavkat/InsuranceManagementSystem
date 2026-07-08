import { create } from "zustand";
import type { NotificationType } from "@/lib/notification-types";

// --- Types ---

export interface Notification {
  id: string; // Unique ID (UUID from backend or generated)
  type: NotificationType;
  title: string;
  message: string;
  timestamp: number; // Unix timestamp in milliseconds
  read: boolean;
  data?: {
    // Optional: link to relevant page
    entityType?: string; // "estimation" | "customer" | "vehicle" | etc.
    entityId?: string; // ID to navigate to
  };
}

export interface NotificationState {
  // State
  notifications: Notification[];
  unreadCount: number;
  isConnected: boolean; // WebSocket connection status

  // Actions
  addNotification: (
    notification: Omit<Notification, "id" | "timestamp" | "read">,
  ) => void;
  markAsRead: (id: string) => void;
  markAllAsRead: () => void;
  clearAll: () => void;
  setConnected: (connected: boolean) => void;
}

// --- Helpers ---

function generateId(): string {
  return `${Date.now()}-${Math.random().toString(36).substring(2, 9)}`;
}

// --- Store ---

export const useNotificationStore = create<NotificationState>()((set) => ({
  // Initial state
  notifications: [],
  unreadCount: 0,
  isConnected: false,

  addNotification: (data) =>
    set((state) => {
      const notification: Notification = {
        ...data,
        id: generateId(),
        timestamp: Date.now(),
        read: false,
      };
      return {
        notifications: [notification, ...state.notifications].slice(0, 100), // Keep last 100
        unreadCount: state.unreadCount + 1,
      };
    }),

  markAsRead: (id) =>
    set((state) => {
      const notifications = state.notifications.map((n) =>
        n.id === id ? { ...n, read: true } : n,
      );
      return {
        notifications,
        unreadCount: notifications.filter((n) => !n.read).length,
      };
    }),

  markAllAsRead: () =>
    set((state) => ({
      notifications: state.notifications.map((n) => ({ ...n, read: true })),
      unreadCount: 0,
    })),

  clearAll: () =>
    set({
      notifications: [],
      unreadCount: 0,
    }),

  setConnected: (connected) => set({ isConnected: connected }),
}));
