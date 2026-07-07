"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";

function makeQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        // Data is considered fresh for 30 seconds — avoids refetching on every mount
        staleTime: 30 * 1000,
        // Retry failed queries up to 2 times with exponential backoff
        retry: 2,
        // Don't refetch when the browser window regains focus (avoid unnecessary requests)
        refetchOnWindowFocus: false,
      },
      mutations: {
        // Retry failed mutations once
        retry: 1,
      },
    },
  });
}

let browserQueryClient: QueryClient | undefined;

function getQueryClient() {
  // Server: always create a new QueryClient (prevents cross-request state leakage)
  if (typeof window === "undefined") {
    return makeQueryClient();
  }
  // Browser: reuse the same QueryClient across the app lifecycle
  if (!browserQueryClient) {
    browserQueryClient = makeQueryClient();
  }
  return browserQueryClient;
}

export function QueryProvider({ children }: { children: ReactNode }) {
  const [queryClient] = useState(getQueryClient);

  return (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}
