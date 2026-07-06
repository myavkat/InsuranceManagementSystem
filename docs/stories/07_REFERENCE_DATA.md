# Story: Reference Data Browsing

## Description
As any user, I want to browse reference data (cities, professions) so that I can select correct values in forms.

---

## Scenarios

### 1. List Cities
- **Given** I am on a form that requires city selection
- **When** I open the city dropdown
- **Then** I see an alphabetically sorted list of all cities with their plate codes

### 2. List Professions
- **Given** I am on a form that requires profession selection (e.g., customer creation)
- **When** I open the profession dropdown
- **Then** I see an alphabetically sorted list of all professions

### 3. Domain Event Publication
- **Given** reference data changes (e.g., a city is added)
- **When** the Reference Data Service publishes the change
- **Then** consumers (cache invalidation, dependent services) receive the update via `reference-data.events` topic

---

## Acceptance Criteria

- Reference data is seeded with Turkish cities (81 cities) and common professions
- API endpoint returns data with caching headers (CDN or in-memory cache)
- Domain events published to `reference-data.events` topic for cache invalidation
- Domain events published on reference data changes for cache invalidation
- City list includes city name and plate code (e.g., "06 - Ankara")
