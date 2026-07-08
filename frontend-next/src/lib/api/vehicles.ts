import { apiClient } from "./client";
import type { PageResponse } from "./types";

// --- Reference Data ---

export interface CarBrand {
  id: number;
  name: string;
}

export interface CarModel {
  id: number;
  name: string;
  carBrandId: number;
}

export interface CarEngine {
  id: number;
  name: string;
  volume?: number;
  power?: number;
}

export interface CarFuelType {
  id: number;
  name: string;
}

export interface CarType {
  id: number;
  name: string;
}

export interface CarPackage {
  id: number;
  name: string;
}

// --- Vehicle ---

export interface VehicleResponse {
  id: string;
  plate?: string;
  chassisNumber?: string;
  licenseFirstDate?: string;
  carBrandId?: number;
  carBrandName?: string;
  carModelId?: number;
  carModelName?: string;
  carEngineId?: number;
  carEngineName?: string;
  carFuelTypeId?: number;
  carFuelTypeName?: string;
  carTypeId?: number;
  carTypeName?: string;
  carPackageId?: number;
  carPackageName?: string;
  customerId: string;
  customerName?: string;
  createdAt: string;
  updatedAt?: string;
}

export interface VehicleRequest {
  plate?: string;
  chassisNumber?: string;
  licenseFirstDate?: string;
  carBrandId?: number;
  carModelId?: number;
  carEngineId?: number;
  carFuelTypeId?: number;
  carTypeId?: number;
  carPackageId?: number;
  customerId: string;
}

// --- API Functions ---

export async function getVehicles(
  page = 0,
  size = 20,
  search?: string,
  sort?: string,
  direction?: string,
): Promise<PageResponse<VehicleResponse>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (search) params.set("search", search);
  if (sort && direction) {
    params.set("sort", `${sort},${direction}`);
  } else if (sort) {
    params.set("sort", sort);
  }
  return apiClient<PageResponse<VehicleResponse>>(
    `/api/vehicles?${params.toString()}`
  );
}

export async function getVehicle(id: string): Promise<VehicleResponse> {
  return apiClient<VehicleResponse>(`/api/vehicles/${id}`);
}

export async function createVehicle(
  data: VehicleRequest
): Promise<VehicleResponse> {
  return apiClient<VehicleResponse>("/api/vehicles", {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function updateVehicle(
  id: string,
  data: Partial<VehicleRequest>
): Promise<VehicleResponse> {
  return apiClient<VehicleResponse>(`/api/vehicles/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
  });
}

export async function deleteVehicle(id: string): Promise<void> {
  return apiClient<void>(`/api/vehicles/${id}`, { method: "DELETE" });
}

// --- Reference Data Functions ---

export async function getBrands(): Promise<CarBrand[]> {
  return apiClient<CarBrand[]>("/api/vehicles/brands");
}

export async function getModelsByBrand(brandId: number): Promise<CarModel[]> {
  return apiClient<CarModel[]>(`/api/vehicles/brands/${brandId}/models`);
}

export async function getEngines(): Promise<CarEngine[]> {
  return apiClient<CarEngine[]>("/api/vehicles/engines");
}

export async function getFuelTypes(): Promise<CarFuelType[]> {
  return apiClient<CarFuelType[]>("/api/vehicles/fuel-types");
}

export async function getTypes(): Promise<CarType[]> {
  return apiClient<CarType[]>("/api/vehicles/types");
}

export async function getPackages(): Promise<CarPackage[]> {
  return apiClient<CarPackage[]>("/api/vehicles/packages");
}
