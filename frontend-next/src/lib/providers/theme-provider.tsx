"use client";

import { useEffect, type ReactNode } from "react";
import { useUIStore } from "@/lib/store/ui-store";

export function ThemeProvider({ children }: { children: ReactNode }) {
  const theme = useUIStore((state) => state.theme);

  useEffect(() => {
    const root = document.documentElement;

    if (theme === "system") {
      const mediaQuery = window.matchMedia("(prefers-color-scheme: dark)");
      const apply = () => {
        root.classList.toggle("dark", mediaQuery.matches);
      };
      apply();
      mediaQuery.addEventListener("change", apply);
      return () => mediaQuery.removeEventListener("change", apply);
    } else if (theme === "dark") {
      root.classList.add("dark");
    } else {
      root.classList.remove("dark");
    }
  }, [theme]);

  return <>{children}</>;
}
