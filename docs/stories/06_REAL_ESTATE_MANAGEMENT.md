# Story: Real Estate Information Management

## Description
As an agent, I want to manage real estate information so that I can link properties to customers and use them in insurance estimations (e.g., DASK).

---

## Scenarios

### 1. List Real Estate
- **Given** I am logged in as an agent
- **When** I navigate to the real estate page
- **Then** I see a paginated table of all properties with address, city, square meters, construction year, and customer name

### 2. Create Real Estate
- **Given** I am on the new real estate form
- **When** I select a customer, fill in address, city, district, square meters, construction year, construction type, luxury class, and usage type
- **Then** the property record is created and linked to the customer

### 3. Update Real Estate
- **Given** I am viewing a property detail
- **When** I update any field (address, square meters, etc.)
- **Then** the changes are saved

### 4. Delete Real Estate
- **Given** I am viewing a property detail
- **When** I delete the property
- **Then** it is removed from the system

### 5. Browse Reference Data (Construction Types, Luxury Classes, Usage Types)
- **Given** I am on the real estate form
- **When** I open dropdowns for construction type, luxury class, or usage type
- **Then** I see the available options populated from reference data

---

## Acceptance Criteria

- Square meters must be a positive number
- Construction year cannot be in the future
- Address is a required field
- A property must be linked to an existing customer
- Construction type, luxury class, and usage type reference data is managed by RealEstate Service
