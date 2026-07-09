import { apiClient } from "./client";
import type { PageResponse } from "./types";

// --- Types ---

export interface InsuranceTypeResponse {
  id: number;
  name: string;
}

export interface InsuranceCompanyResponse {
  id: number;
  name: string;
  rating?: number;
  isActive: boolean;
}

export interface InsuranceResponse {
  id: string;
  name: string;
  description?: string;
  typeId: number;
  typeName?: string;
  companyId: number;
  companyName?: string;
  basePremium: number;
  isActive: boolean;
  createdAt: string;
  updatedAt?: string;
}

export interface InsuranceRequest {
  name: string;
  description?: string;
  typeId: number;
  companyId?: number;
  basePremium: number;
  isActive: boolean;
}

// --- API Functions ---

export async function getInsurances(
  page = 0,
  size = 20,
  typeId?: number,
  companyId?: number,
  search?: string,
  sort?: string,
  direction?: string,
): Promise<PageResponse<InsuranceResponse>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (typeId != null) params.set("typeId", String(typeId));
  if (companyId != null) params.set("companyId", String(companyId));
  if (search) params.set("search", search);
  if (sort && direction) {
    params.set("sort", `${sort},${direction}`);
  } else if (sort) {
    params.set("sort", sort);
  }
  return apiClient<PageResponse<InsuranceResponse>>(
    `/api/insurances?${params.toString()}`
  );
}

export async function getInsurance(id: string): Promise<InsuranceResponse> {
  return apiClient<InsuranceResponse>(`/api/insurances/${id}`);
}

export async function createInsurance(
  data: InsuranceRequest
): Promise<InsuranceResponse> {
  return apiClient<InsuranceResponse>("/api/insurances", {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function updateInsurance(
  id: string,
  data: Partial<InsuranceRequest>
): Promise<InsuranceResponse> {
  return apiClient<InsuranceResponse>(`/api/insurances/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
  });
}

export async function deleteInsurance(id: string): Promise<void> {
  return apiClient<void>(`/api/insurances/${id}`, { method: "DELETE" });
}

export async function getInsuranceTypes(): Promise<InsuranceTypeResponse[]> {
  return apiClient<InsuranceTypeResponse[]>("/api/insurances/types");
}

export async function deactivateInsurance(id: string): Promise<InsuranceResponse> {
  return apiClient<InsuranceResponse>(`/api/insurances/${id}/deactivate`, {
    method: "PATCH",
  });
}

export async function getInsuranceCompanies(): Promise<InsuranceCompanyResponse[]> {
  return apiClient<InsuranceCompanyResponse[]>("/api/insurances/companies");
}

// --- Risk Factor Types ---

export interface RiskFactorResponse {
  id: string;
  insuranceId: string;
  factorName: string;
  factorValue: number;
  createdAt: string;
  updatedAt: string;
}

export interface RiskFactorUpdateRequest {
  factorName: string;
  factorValue: number;
}

export interface RiskFactorHistoryResponse {
  id: string;
  riskFactorId: string;
  insuranceId: string;
  factorName: string;
  oldValue: number | null;
  newValue: number;
  changedAt: string;
}

// --- Risk Factor API Functions ---

export async function getRiskFactors(insuranceId: string): Promise<RiskFactorResponse[]> {
  return apiClient<RiskFactorResponse[]>(`/api/insurances/${insuranceId}/risk-factors`);
}

export async function updateRiskFactors(
  insuranceId: string,
  updates: RiskFactorUpdateRequest[],
): Promise<RiskFactorResponse[]> {
  return apiClient<RiskFactorResponse[]>(`/api/insurances/${insuranceId}/risk-factors`, {
    method: "PUT",
    body: JSON.stringify(updates),
  });
}

export async function getRiskFactorHistory(
  insuranceId: string,
  page = 0,
  size = 20,
): Promise<PageResponse<RiskFactorHistoryResponse>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  return apiClient<PageResponse<RiskFactorHistoryResponse>>(
    `/api/insurances/${insuranceId}/risk-factors/history?${params.toString()}`,
  );
}
