import { apiClient } from "./client";
import type { PageResponse } from "./types";

export interface InsuranceResponse {
  id: string;
  name: string;
  description?: string;
  typeId: number;
  typeName?: string;
  basePremium: number;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export async function getInsurances(
  page = 0,
  size = 20,
  typeId?: number,
  search?: string
): Promise<PageResponse<InsuranceResponse>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (typeId != null) params.set("typeId", String(typeId));
  if (search) params.set("search", search);
  return apiClient<PageResponse<InsuranceResponse>>(
    `/api/insurances?${params.toString()}`
  );
}

export async function getInsurance(id: string): Promise<InsuranceResponse> {
  return apiClient<InsuranceResponse>(`/api/insurances/${id}`);
}
