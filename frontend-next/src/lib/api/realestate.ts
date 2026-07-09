import { apiClient } from "./client";
import type { PageResponse } from "./types";

// --- Reference Data ---

export interface ConstructionType {
  id: number;
  name: string;
}

export interface LuxuryClass {
  id: number;
  name: string;
}

export interface UsageType {
  id: number;
  name: string;
}

// --- Real Estate ---

export interface RealEstateResponse {
  id: string;
  address: string;
  cityId?: number;
  cityName?: string;
  district?: string;
  squareMeters: number;
  constructionYear?: number;
  constructionTypeId?: number;
  constructionTypeName?: string;
  luxuryClassId?: number;
  luxuryClassName?: string;
  usageTypeId?: number;
  usageTypeName?: string;
  customerId: string;
  customerName?: string;
  createdAt: string;
  updatedAt?: string;
}

export interface RealEstateRequest {
  address: string;
  cityId?: number;
  district?: string;
  squareMeters: number;
  constructionYear?: number;
  constructionTypeId?: number;
  luxuryClassId?: number;
  usageTypeId?: number;
  customerId: string;
}

// --- API Functions ---

export async function getRealEstates(
  page = 0,
  size = 20,
  search?: string,
  sort?: string,
  direction?: string,
  customerId?: string,
): Promise<PageResponse<RealEstateResponse>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (search) params.set("search", search);
  if (sort && direction) {
    params.set("sort", `${sort},${direction}`);
  } else if (sort) {
    params.set("sort", sort);
  }
  if (customerId) params.set("customerId", customerId);
  return apiClient<PageResponse<RealEstateResponse>>(
    `/api/real-estate?${params.toString()}`
  );
}

export async function getRealEstate(id: string): Promise<RealEstateResponse> {
  return apiClient<RealEstateResponse>(`/api/real-estate/${id}`);
}

export async function createRealEstate(
  data: RealEstateRequest
): Promise<RealEstateResponse> {
  return apiClient<RealEstateResponse>("/api/real-estate", {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function updateRealEstate(
  id: string,
  data: Partial<RealEstateRequest>
): Promise<RealEstateResponse> {
  return apiClient<RealEstateResponse>(`/api/real-estate/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
  });
}

export async function deleteRealEstate(id: string): Promise<void> {
  return apiClient<void>(`/api/real-estate/${id}`, { method: "DELETE" });
}

// --- Reference Data Functions ---

export async function getConstructionTypes(): Promise<ConstructionType[]> {
  return apiClient<ConstructionType[]>("/api/real-estate/construction-types");
}

export async function getLuxuryClasses(): Promise<LuxuryClass[]> {
  return apiClient<LuxuryClass[]>("/api/real-estate/luxury-classes");
}

export async function getUsageTypes(): Promise<UsageType[]> {
  return apiClient<UsageType[]>("/api/real-estate/usage-types");
}
