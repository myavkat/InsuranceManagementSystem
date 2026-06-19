# Story: Insurance Product Management

## Description
As an admin, I want to manage insurance products, types, and companies so that agents can offer up-to-date insurance options to customers.

---

## Scenarios

### 1. List Insurance Products
- **Given** I am logged in as an admin
- **When** I navigate to the insurances page
- **Then** I see a list of all insurance products with name, type, company, base premium, and active status

### 2. Create Insurance Product
- **Given** I am on the new insurance form
- **When** I fill in name, description, select an insurance type and company, set base premium, and mark as active
- **Then** the insurance product is created and appears in the list

### 3. Update Insurance Product
- **Given** I am viewing an insurance product
- **When** I update any field (premium, name, active status)
- **Then** the changes are saved and the product is updated

### 4. Deactivate Insurance Product
- **Given** I am viewing an insurance product
- **When** I deactivate it
- **Then** the product is soft-deleted (no longer offered in new estimations)

### 5. Manage Insurance Types
- **Given** I am an admin
- **When** I navigate to insurance types management
- **Then** I can view and create insurance types (TRAFFIC, CASCO, DASK, HEALTH, LIFE, etc.)

### 6. Manage Insurance Companies
- **Given** I am an admin
- **When** I navigate to insurance companies management
- **Then** I can view, create, and edit insurance companies with name and rating

---

## Acceptance Criteria

- Insurance types are predefined (TRAFFIC, CASCO, DASK, HEALTH, LIFE)
- Companies have a rating field
- Base premium is a positive decimal value
- Only active products appear in estimation forms
- Domain events published on create/update for cache invalidation
