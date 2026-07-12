"use client";

import { useEffect, useRef } from "react";

/**
 * Warn the user before navigating away when the form has unsaved changes.
 *
 * Covers:
 * - Browser tab close / refresh (beforeunload)
 * - Browser back/forward (popstate)
 * - Next.js client-side Link clicks and router.push (history.pushState/replaceState patching)
 *
 * @param isDirty - Whether the form has been modified (from RHF formState.isDirty)
 * @param message - Warning message (used by beforeunload and confirm dialogs)
 */
export function useUnsavedChanges(
  isDirty: boolean,
  message = "You have unsaved changes. Leave anyway?"
) {
  const isDirtyRef = useRef(isDirty);
  isDirtyRef.current = isDirty;

  // 1. Browser-level: warn on tab close / refresh / back button
  useEffect(() => {
    if (!isDirty) return;

    const handler = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      // Modern browsers ignore the message string, but setting returnValue triggers the dialog
      e.returnValue = message;
      return message;
    };

    window.addEventListener("beforeunload", handler);
    return () => window.removeEventListener("beforeunload", handler);
  }, [isDirty, message]);

  // 2. Next.js client-side: warn on Link clicks and router.push
  // We intercept history.pushState and history.replaceState,
  // which Next.js uses under the hood for client-side navigation.
  useEffect(() => {
    if (!isDirty) return;

    const originalPushState = window.history.pushState.bind(window.history);
    const originalReplaceState = window.history.replaceState.bind(window.history);

    const confirmNavigation = (): boolean => {
      if (isDirtyRef.current) {
        return window.confirm(message);
      }
      return true;
    };

    window.history.pushState = function (...args) {
      if (!confirmNavigation()) return;
      return originalPushState(...args);
    };

    window.history.replaceState = function (...args) {
      if (!confirmNavigation()) return;
      return originalReplaceState(...args);
    };

    // Handle browser back/forward buttons
    const handlePopState = (e: PopStateEvent) => {
      if (isDirtyRef.current && !window.confirm(message)) {
        // Push a new state to cancel the navigation
        window.history.pushState(null, "", window.location.href);
        e.preventDefault();
      }
    };

    window.addEventListener("popstate", handlePopState);

    return () => {
      window.history.pushState = originalPushState;
      window.history.replaceState = originalReplaceState;
      window.removeEventListener("popstate", handlePopState);
    };
  }, [isDirty, message]);
}
