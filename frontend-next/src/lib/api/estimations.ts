import { apiClient } from "./client";
import type { PageResponse } from "./types";

export interface EstimationResponse {
  id: string;
  customerId: string;
  vehicleId?: string;
  realEstateId?: string;
  insuranceTypeId: number;
  premium?: number;
  status: string;
  createdAt: string;
}

export interface EstimationRequest {
  customerId: string;
  vehicleId?: string;
  realEstateId?: string;
  insuranceTypeId: number;
}

export async function getEstimations(
  page = 0,
  size = 20
): Promise<PageResponse<EstimationResponse>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  return apiClient<PageResponse<EstimationResponse>>(
    `/api/estimations?${params.toString()}`
  );
}

export async function getEstimation(
  id: string
): Promise<EstimationResponse> {
  return apiClient<EstimationResponse>(`/api/estimations/${id}`);
}

export async function createEstimation(
  data: EstimationRequest
): Promise<EstimationResponse> {
  return apiClient<EstimationResponse>("/api/estimations", {
    method: "POST",
    body: JSON.stringify(data),
  });
}
