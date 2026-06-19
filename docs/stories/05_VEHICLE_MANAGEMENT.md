# Story: Vehicle Information Management

## Description
As an agent, I want to manage vehicle information so that I can link vehicles to customers and use them in insurance estimations.

---

## Scenarios

### 1. List Vehicles
- **Given** I am logged in as an agent
- **When** I navigate to the vehicles page
- **Then** I see a paginated table of all vehicles with plate, brand/model, customer name, and license date

### 2. Create Vehicle
- **Given** I am on the new vehicle form
- **When** I select a customer, fill in plate, chassis number, license first date, brand, model, engine, fuel type, type, and package
- **Then** the vehicle is created and linked to the customer

### 3. Update Vehicle
- **Given** I am viewing a vehicle detail
- **When** I update any editable field (plate, engine, package)
- **Then** the changes are saved

### 4. Delete Vehicle
- **Given** I am viewing a vehicle detail
- **When** I delete the vehicle
- **Then** it is removed from the system (hard delete allowed for vehicles)

### 5. Browse Reference Data (Brands, Models, etc.)
- **Given** I am on the vehicle form
- **When** I select a brand
- **Then** the model dropdown is populated with models for that brand
- **And** engine, fuel type, type, and package dropdowns show available options

---

## Acceptance Criteria

- Plate format is validated (Turkish plate format: `XX 1234` or `XX 1234 YY`)
- Chassis number is 17 characters alphanumeric
- Brand/model selection cascades (selecting brand filters models)
- A vehicle must be linked to an existing customer
- All reference data (brands, models, engines, fuel types, types, packages) is seeded and served by Vehicle Service
