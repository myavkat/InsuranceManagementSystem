export { apiClient } from "./client";
export { ApiError, type ApiResponse, type PageResponse } from "./types";

// Re-export all domain modules so consumers can import from a single path:
// import { authApi, customersApi } from "@/lib/api";
export * as authApi from "./auth";
export * as customersApi from "./customers";
export * as insurancesApi from "./insurances";
export * as estimationsApi from "./estimations";
export * as vehiclesApi from "./vehicles";
export * as realEstateApi from "./realestate";
export * as referenceDataApi from "./reference-data";
export * as dashboardApi from "./dashboard";
