"use client";

import { useEffect } from "react";

/**
 * Warn the user before navigating away when the form has unsaved changes.
 *
 * @param isDirty - Whether the form has been modified (from RHF formState.isDirty)
 * @param message - Custom warning message (only used by beforeunload; browsers ignore custom messages in modern versions)
 */
export function useUnsavedChanges(isDirty: boolean, message = "You have unsaved changes. Leave anyway?") {
  // Browser-level: warn on tab close / refresh / back button
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
}
