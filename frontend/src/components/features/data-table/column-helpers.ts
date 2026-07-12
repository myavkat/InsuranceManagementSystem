import { createColumnHelper } from "@tanstack/react-table";

/**
 * Format a date string for display in tables.
 * Falls back gracefully for invalid or missing dates.
 */
export function formatDate(dateStr: string): string {
  if (!dateStr) return "—";
  try {
    return new Date(dateStr).toLocaleDateString("en-US", {
      year: "numeric",
      month: "short",
      day: "numeric",
    });
  } catch {
    return dateStr;
  }
}

/**
 * Format a numeric amount as Turkish Lira currency.
 */
export function formatCurrency(value: number | null | undefined): string {
  if (value == null) return "—";
  try {
    return new Intl.NumberFormat("tr-TR", {
      style: "currency",
      currency: "TRY",
    }).format(value);
  } catch {
    return String(value);
  }
}

/**
 * Generic column helper factory for type-safe column definitions.
 */
export function createColumns<T>() {
  return createColumnHelper<T>();
}
