// Generic API response envelope from the API Gateway.
// All backend responses are wrapped in this structure.
export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  message?: string;
  errors?: Record<string, string[]>;
  timestamp?: string;
}

// Custom error class for API errors.
// Thrown when the API returns success: false or a non-2xx status.
export class ApiError extends Error {
  status: number;
  errors?: Record<string, string[]>;

  constructor(status: number, message: string, errors?: Record<string, string[]>) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.errors = errors;
  }
}

// Paginated response wrapper (used by list endpoints)
export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number; // current page (0-indexed)
  size: number; // page size
}
