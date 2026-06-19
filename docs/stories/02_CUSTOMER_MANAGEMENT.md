# Story: Customer Management

## Description
As an agent, I want to manage customer information so that I can keep records up to date and process insurance estimations.

---

## Scenarios

### 1. List Customers
- **Given** I am logged in as an agent
- **When** I navigate to the customers page
- **Then** I see a paginated table of all customers with name, national ID, email, phone, and city

### 2. Search Customers
- **Given** I am on the customers page
- **When** I type a name or national ID into the search bar
- **Then** the customer list filters in real time to show matching results

### 3. Create Customer
- **Given** I am on the new customer form
- **When** I fill in all required fields (first name, last name, national ID, email, phone, birth date, city, profession, address)
- **Then** the customer is created and I am redirected to the customer detail page

### 4. View Customer Detail
- **Given** I have selected a customer from the list
- **When** I click on the customer
- **Then** I see full customer details including all personal info, linked vehicles, and estimation history

### 5. Update Customer
- **Given** I am viewing a customer detail
- **When** I edit any field and save
- **Then** the customer record is updated and confirmed with a success message

### 6. Delete Customer (Soft)
- **Given** I am viewing a customer detail
- **When** I delete the customer
- **Then** the customer is soft-deleted (hidden from list) but their historical data remains for compliance

---

## Acceptance Criteria

- National ID (TCKN) is validated for format
- Email and phone format validated
- City and profession are selected from reference data
- Customer cannot be deleted if they have active estimations
- Deletion is soft (sets `deleted_at` timestamp)
