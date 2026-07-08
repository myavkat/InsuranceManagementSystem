import { apiClient } from "./client";
import type { PageResponse } from "./types";

export interface CustomerResponse {
  id: string;
  firstName: string;
  lastName: string;
  nationalId: string;
  email: string;
  phone?: string;
  birthDate?: string;
  address?: string;
  cityId?: number;
  cityName?: string;
  professionId?: number;
  professionName?: string;
  createdAt: string;
  updatedAt?: string;
}

export interface CustomerRequest {
  firstName: string;
  lastName: string;
  nationalId: string;
  email: string;
  phone?: string;
  birthDate?: string;
  address?: string;
  cityId?: number;
  professionId?: number;
}

export async function getCustomers(
  page = 0,
  size = 20,
  search?: string
): Promise<PageResponse<CustomerResponse>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (search) params.set("search", search);
  return apiClient<PageResponse<CustomerResponse>>(
    `/api/customers?${params.toString()}`
  );
}

export async function getCustomer(id: string): Promise<CustomerResponse> {
  return apiClient<CustomerResponse>(`/api/customers/${id}`);
}

export async function createCustomer(
  data: CustomerRequest
): Promise<CustomerResponse> {
  return apiClient<CustomerResponse>("/api/customers", {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function updateCustomer(
  id: string,
  data: Partial<CustomerRequest>
): Promise<CustomerResponse> {
  return apiClient<CustomerResponse>(`/api/customers/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
  });
}

export async function deleteCustomer(id: string): Promise<void> {
  return apiClient<void>(`/api/customers/${id}`, { method: "DELETE" });
}

export async function checkNationalId(nationalId: string): Promise<boolean> {
  // Returns true if the nationalId is available (not taken)
  // The backend should have an endpoint like GET /api/customers/check-national-id?id=xxx
  try {
    const result = await apiClient<{ available: boolean }>(
      `/api/customers/check-national-id?nationalId=${encodeURIComponent(nationalId)}`
    );
    return result.available;
  } catch {
    return false; // Assume taken if check fails
  }
}
