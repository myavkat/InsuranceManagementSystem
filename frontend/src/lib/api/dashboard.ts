import { apiClient } from "./client";

export interface Kpis {
  activePolicies: number;
  pendingApprovals: number;
  totalPremiumVolume: number;
  totalPremiumVolumeFormatted: string;
  newCustomers30d: number;
  estimationsByStatus: Record<string, number>;
}

export interface PendingAction {
  sagaId: string;
  customerName: string;
  insuranceType: string;
  premium: number;
  premiumFormatted: string;
  ageInMinutes: number;
  timeoutDeadline: string;
  status: string;
}

export interface RecentActivity {
  eventType: string;
  sagaId: string;
  timestamp: string;
  description: string;
}

export interface DashboardSummary {
  kpis: Kpis;
  pendingActions: PendingAction[];
  recentActivity: RecentActivity[];
}

export async function getDashboardSummary(): Promise<DashboardSummary> {
  return apiClient<DashboardSummary>("/api/dashboard/summary");
}
