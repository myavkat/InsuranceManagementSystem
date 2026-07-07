import { create } from "zustand";
import { persist } from "zustand/middleware";

export interface UserInfo {
  userId: string;
  username: string;
  email: string;
  roles: string[];
}

export interface AuthState {
  // State
  accessToken: string | null;
  refreshToken: string | null;
  expiresAt: number | null; // Unix timestamp in milliseconds
  user: UserInfo | null;
  isAuthenticated: boolean;

  // Actions
  login: (accessToken: string, refreshToken: string, expiresIn: number, user: UserInfo) => void;
  logout: () => void;
  setAccessToken: (token: string, expiresIn: number) => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      // Initial state
      accessToken: null,
      refreshToken: null,
      expiresAt: null,
      user: null,
      isAuthenticated: false,

      // Login action — called after successful POST /api/auth/login
      login: (accessToken, refreshToken, expiresIn, user) =>
        set({
          accessToken,
          refreshToken,
          expiresAt: Date.now() + expiresIn * 1000,
          user,
          isAuthenticated: true,
        }),

      // Logout action — clears all auth state
      logout: () =>
        set({
          accessToken: null,
          refreshToken: null,
          expiresAt: null,
          user: null,
          isAuthenticated: false,
        }),

      // Refresh token rotation — called after successful POST /api/auth/refresh
      setAccessToken: (token, expiresIn) =>
        set({
          accessToken: token,
          expiresAt: Date.now() + expiresIn * 1000,
        }),
    }),
    {
      name: "ims-auth-storage", // localStorage key
      // Only persist these fields to localStorage
      partialize: (state) => ({
        accessToken: state.accessToken,
        refreshToken: state.refreshToken,
        expiresAt: state.expiresAt,
        user: state.user,
        isAuthenticated: state.isAuthenticated,
      }),
    }
  )
);
