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
  search?: string,
  sort?: string,
  direction?: string,
): Promise<PageResponse<CustomerResponse>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (search) params.set("search", search);
  if (sort && direction) {
    // Spring Data Pageable format: sort=field,direction
    params.set("sort", `${sort},${direction}`);
  } else if (sort) {
    params.set("sort", sort);
  }
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

export async function checkNationalId(
  nationalId: string,
  signal?: AbortSignal,
): Promise<boolean> {
  // Returns true if the nationalId is available (not taken).
  // Throws on network/HTTP errors — callers should handle errors gracefully
  // rather than silently treating them as "taken".
  const result = await apiClient<{ available: boolean }>(
    `/api/customers/check-national-id?nationalId=${encodeURIComponent(nationalId)}`,
    { signal },
  );
  return result.available;
}
