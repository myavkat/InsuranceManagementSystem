"use client";

import { useUIStore } from "@/lib/store/ui-store";
import { useAuthStore } from "@/lib/store/auth-store";
import { Button } from "@/components/ui/button";
import {
  PanelLeft,
  Bell,
  LogOut,
  User,
  Sun,
  Moon,
  Monitor,
} from "lucide-react";

export function Header() {
  const { toggleSidebar, theme, setTheme } = useUIStore();
  const { user, logout, isAuthenticated } = useAuthStore();

  const cycleTheme = () => {
    const next: Record<string, "light" | "dark" | "system"> = {
      light: "dark",
      dark: "system",
      system: "light",
    };
    setTheme(next[theme]);
  };

  const ThemeIcon = theme === "dark" ? Moon : theme === "light" ? Sun : Monitor;

  return (
    <header className="sticky top-0 z-30 flex h-14 items-center gap-4 border-b bg-background px-4">
      {/* Mobile sidebar toggle */}
      <Button
        variant="ghost"
        size="icon"
        onClick={toggleSidebar}
        className="lg:hidden"
        aria-label="Toggle sidebar"
      >
        <PanelLeft className="h-5 w-5" />
      </Button>

      {/* Spacer */}
      <div className="flex-1" />

      {/* Theme toggle */}
      <Button
        variant="ghost"
        size="icon"
        onClick={cycleTheme}
        aria-label={`Current theme: ${theme}. Click to switch.`}
      >
        <ThemeIcon className="h-5 w-5" />
      </Button>

      {/* Notifications (placeholder) */}
      <Button variant="ghost" size="icon" aria-label="Notifications">
        <Bell className="h-5 w-5" />
      </Button>

      {/* User section */}
      {isAuthenticated && user ? (
        <div className="flex items-center gap-2">
          <div className="hidden text-sm sm:block">
            <p className="font-medium leading-none">{user.username}</p>
            <p className="text-xs text-muted-foreground">{user.email}</p>
          </div>
          <Button variant="ghost" size="icon" aria-label="User menu">
            <User className="h-5 w-5" />
          </Button>
          <Button
            variant="ghost"
            size="icon"
            onClick={logout}
            aria-label="Sign out"
          >
            <LogOut className="h-5 w-5" />
          </Button>
        </div>
      ) : (
        <div className="text-sm text-muted-foreground">Not signed in</div>
      )}
    </header>
  );
}
