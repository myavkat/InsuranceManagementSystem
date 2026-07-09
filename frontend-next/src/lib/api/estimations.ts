import { apiClient } from "./client";
import type { PageResponse } from "./types";

export type EstimationStatus = "STARTED" | "COMPLETED" | "REJECTED";

export interface EstimationResponse {
  id: string;
  sagaId?: string;
  customerId: string;
  customerName?: string;
  customerNationalId?: string;
  vehicleId?: string;
  vehiclePlate?: string;
  vehicleChassisNumber?: string;
  realEstateId?: string;
  realEstateAddress?: string;
  insuranceId: string;
  insuranceName?: string;
  insuranceTypeName?: string;
  premium?: number;
  status: EstimationStatus;
  details?: string;
  createdAt: string;
  updatedAt?: string;
}

export interface EstimationRequest {
  customerId: string;
  vehicleId?: string;
  realEstateId?: string;
  insuranceId: string;
}

export interface EstimationListParams {
  page?: number;
  size?: number;
  status?: EstimationStatus;
  customerId?: string;
  dateFrom?: string;
  dateTo?: string;
  sort?: string;
  direction?: string;
}

export async function getEstimations(
  params: EstimationListParams = {}
): Promise<PageResponse<EstimationResponse>> {
  const { page = 0, size = 20, status, customerId, dateFrom, dateTo, sort, direction } = params;
  const searchParams = new URLSearchParams({ page: String(page), size: String(size) });
  if (status) searchParams.set("status", status);
  if (customerId) searchParams.set("customerId", customerId);
  if (dateFrom) searchParams.set("dateFrom", dateFrom);
  if (dateTo) searchParams.set("dateTo", dateTo);
  if (sort) searchParams.set("sort", sort);
  if (direction) searchParams.set("direction", direction);
  return apiClient<PageResponse<EstimationResponse>>(
    `/api/estimations?${searchParams.toString()}`
  );
}

export async function getEstimation(id: string): Promise<EstimationResponse> {
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
