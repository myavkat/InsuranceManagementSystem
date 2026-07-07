import { apiClient } from "./client";
import type { PageResponse } from "./types";

export interface VehicleResponse {
  id: string;
  plate?: string;
  brand?: string;
  model?: string;
  year?: number;
  engineType?: string;
  fuelType?: string;
  customerId?: string;
}

export async function getVehicles(
  page = 0,
  size = 20
): Promise<PageResponse<VehicleResponse>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  return apiClient<PageResponse<VehicleResponse>>(
    `/api/vehicles?${params.toString()}`
  );
}

export async function getVehicle(id: string): Promise<VehicleResponse> {
  return apiClient<VehicleResponse>(`/api/vehicles/${id}`);
}
