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
