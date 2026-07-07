import { apiClient } from "./client";
import type { PageResponse } from "./types";

export interface CustomerResponse {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
  birthDate?: string;
  createdAt: string;
}

export interface CustomerRequest {
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
  birthDate?: string;
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
  data: CustomerRequest
): Promise<CustomerResponse> {
  return apiClient<CustomerResponse>(`/api/customers/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
  });
}

export async function deleteCustomer(id: string): Promise<void> {
  return apiClient<void>(`/api/customers/${id}`, { method: "DELETE" });
}
