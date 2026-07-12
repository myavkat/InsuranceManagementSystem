# Plan: API Client Library — Complete All Domain Modules

## Objective

Complete the API client library in `frontend/src/lib/api/` so every domain has full TypeScript types and functions for all CRUD operations plus reference data lookups. Add the missing `realestate.ts` and `reference-data.ts` modules.

## Prerequisites

- Plan `01_BFF_ROUTE_HANDLERS.md` must be completed first (BFF routes must proxy to Gateway)

## Files to Read First

- `docs/outlines/02_MICROSERVICES_SPECIFICATIONS.md` — All entity fields and endpoints
- `docs/stories/02_CUSTOMER_MANAGEMENT.md` — Customer fields
- `docs/stories/03_INSURANCE_PRODUCTS.md` — Insurance fields
- `docs/stories/04_ESTIMATION_SAGA.md` — Estimation fields
- `docs/stories/05_VEHICLE_MANAGEMENT.md` — Vehicle fields and reference data
- `docs/stories/06_REAL_ESTATE_MANAGEMENT.md` — Real estate fields and reference data
- `frontend/src/lib/api/client.ts` — The `apiClient<T>()` function
- `frontend/src/lib/api/types.ts` — `PageResponse<T>`, `ApiResponse<T>`, `ApiError`
- `frontend/src/lib/api/customers.ts` — Existing pattern to follow
- `frontend/src/lib/api/index.ts` — Re-export barrel file

## Context

The `apiClient<T>()` function is the canonical fetch wrapper. It:
- Prepends `NEXT_PUBLIC_GATEWAY_URL`
- Attaches JWT Bearer token from `useAuthStore`
- Handles 401 → token refresh → retry
- Parses `ApiResponse<T>` envelope, throws `ApiError` on failure
- Returns unwrapped `T` data

All domain API functions follow this pattern:
- Export TypeScript interfaces for request/response types
- Export async functions that call `apiClient<T>(path, options)`
- Paths are relative to the Gateway (e.g., `/api/customers`)

## Steps

### Step 1: Update `customers.ts` with complete types

File: `frontend/src/lib/api/customers.ts`

Replace the entire file. The `CustomerResponse` interface must include all fields from the Customer entity:

```typescript
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
```

### Step 2: Update `insurances.ts` with complete types and functions

File: `frontend/src/lib/api/insurances.ts`

Replace the entire file. Add:
- `InsuranceCompanyResponse` and `InsuranceTypeResponse` interfaces
- CRUD functions for insurances (create, update, delete)
- `getInsuranceTypes()` and `getInsuranceCompanies()` functions
- `InsuranceType` enum: TRAFFIC, CASCO, DASK, HEALTH, LIFE

```typescript
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
  companyId: number;
  basePremium: number;
  isActive: boolean;
}

// --- API Functions ---

export async function getInsurances(
  page = 0,
  size = 20,
  typeId?: number,
  companyId?: number,
  search?: string
): Promise<PageResponse<InsuranceResponse>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (typeId != null) params.set("typeId", String(typeId));
  if (companyId != null) params.set("companyId", String(companyId));
  if (search) params.set("search", search);
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

export async function getInsuranceCompanies(): Promise<InsuranceCompanyResponse[]> {
  return apiClient<InsuranceCompanyResponse[]>("/api/insurances/companies");
}
```

### Step 3: Update `estimations.ts` with complete types

File: `frontend/src/lib/api/estimations.ts`

Replace the entire file. Add:
- Full `EstimationResponse` with customer name, status, premium, date fields
- Filter params for list (status, customerId, dateFrom, dateTo)

```typescript
import { apiClient } from "./client";
import type { PageResponse } from "./types";

export type EstimationStatus = "STARTED" | "COMPLETED" | "REJECTED";

export interface EstimationResponse {
  id: string;
  sagaId?: string;
  customerId: string;
  customerName?: string;
  vehicleId?: string;
  vehiclePlate?: string;
  realEstateId?: string;
  realEstateAddress?: string;
  insuranceTypeId: number;
  insuranceTypeName?: string;
  companyId?: number;
  companyName?: string;
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
  insuranceTypeId: number;
  companyId?: number;
}

export interface EstimationListParams {
  page?: number;
  size?: number;
  status?: EstimationStatus;
  customerId?: string;
  dateFrom?: string;
  dateTo?: string;
}

export async function getEstimations(
  params: EstimationListParams = {}
): Promise<PageResponse<EstimationResponse>> {
  const { page = 0, size = 20, status, customerId, dateFrom, dateTo } = params;
  const searchParams = new URLSearchParams({ page: String(page), size: String(size) });
  if (status) searchParams.set("status", status);
  if (customerId) searchParams.set("customerId", customerId);
  if (dateFrom) searchParams.set("dateFrom", dateFrom);
  if (dateTo) searchParams.set("dateTo", dateTo);
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
```

### Step 4: Update `vehicles.ts` with complete types and reference data functions

File: `frontend/src/lib/api/vehicles.ts`

Replace the entire file. Add:
- Full `VehicleResponse` and `VehicleRequest` interfaces
- CRUD functions
- Reference data functions for brands, models, engines, fuel types, types, packages

```typescript
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
  search?: string
): Promise<PageResponse<VehicleResponse>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (search) params.set("search", search);
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
```

### Step 5: Create `realestate.ts`

File: `frontend/src/lib/api/realestate.ts` (NEW FILE)

```typescript
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
  search?: string
): Promise<PageResponse<RealEstateResponse>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (search) params.set("search", search);
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
```

### Step 6: Create `reference-data.ts`

File: `frontend/src/lib/api/reference-data.ts` (NEW FILE)

```typescript
import { apiClient } from "./client";

export interface City {
  id: number;
  name: string;
  plateCode?: number;
}

export interface Profession {
  id: number;
  name: string;
}

export async function getCities(): Promise<City[]> {
  return apiClient<City[]>("/api/reference-data/cities");
}

export async function getProfessions(): Promise<Profession[]> {
  return apiClient<Profession[]>("/api/reference-data/professions");
}
```

### Step 7: Update the barrel file

File: `frontend/src/lib/api/index.ts`

Replace the contents. Add the two new modules:

```typescript
export { apiClient } from "./client";
export { ApiError, type ApiResponse, type PageResponse } from "./types";

export * as authApi from "./auth";
export * as customersApi from "./customers";
export * as insurancesApi from "./insurances";
export * as estimationsApi from "./estimations";
export * as vehiclesApi from "./vehicles";
export * as realEstateApi from "./realestate";
export * as referenceDataApi from "./reference-data";
```

### Step 8: Verify the build compiles

Run: `cd frontend && npm run build`

Fix any TypeScript errors before marking this plan complete.

## Acceptance Criteria

- [x] `customers.ts` has all Customer entity fields in `CustomerResponse` and `CustomerRequest`
- [x] `insurances.ts` has `createInsurance`, `updateInsurance`, `deleteInsurance`, `getInsuranceTypes`, `getInsuranceCompanies`
- [x] `estimations.ts` has `EstimationStatus` type, extended filter params, `details` field
- [x] `vehicles.ts` has CRUD functions plus all 6 reference data functions
- [x] `realestate.ts` exists with CRUD functions plus 3 reference data functions
- [x] `reference-data.ts` exists with `getCities()` and `getProfessions()`
- [x] `index.ts` exports all 7 domain modules
- [x] `npm run build` succeeds without TypeScript errors
- [x] All functions use `apiClient<T>()` — no direct `fetch()` calls
