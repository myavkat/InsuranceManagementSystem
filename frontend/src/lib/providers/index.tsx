"use client";

import { type ReactNode } from "react";
import { QueryProvider } from "./query-provider";
import { ThemeProvider } from "./theme-provider";
import { NotificationProvider } from "@/components/features/notification-provider";

export function Providers({ children }: { children: ReactNode }) {
  return (
    <ThemeProvider>
      <QueryProvider>
        <NotificationProvider>{children}</NotificationProvider>
      </QueryProvider>
    </ThemeProvider>
  );
}
