import { create } from "zustand";

type Theme = "light" | "dark" | "system";

export interface UIState {
  // Sidebar
  sidebarOpen: boolean;
  toggleSidebar: () => void;
  setSidebarOpen: (open: boolean) => void;

  // Theme
  theme: Theme;
  setTheme: (theme: Theme) => void;
}

export const useUIStore = create<UIState>()((set) => ({
  // Initial state
  sidebarOpen: false,
  theme: "system",

  // Sidebar actions
  toggleSidebar: () => set((state) => ({ sidebarOpen: !state.sidebarOpen })),
  setSidebarOpen: (open) => set({ sidebarOpen: open }),

  // Theme actions
  setTheme: (theme) => set({ theme }),
}));
