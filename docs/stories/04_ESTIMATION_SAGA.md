# Story: Insurance Estimation (SAGA Flow)

## Description
As an agent, I want to create an insurance estimation for a customer so that I can calculate the premium for a selected insurance product. The estimation process involves multiple services in a distributed transaction via SAGA.

---

## Scenarios

### 1. Start Estimation
- **Given** I am viewing a customer detail
- **When** I click "New Estimation"
- **Then** I select an insurance type and company, optionally link a vehicle or real estate, and submit
- **And** the estimation is created with status `PENDING`

### 2. Successful Estimation
- **Given** I have submitted a valid estimation request
- **When** the customer is valid, the vehicle/real estate is valid, and the premium is calculated
- **Then** the estimation status updates to `COMPLETED` and I see the calculated premium and breakdown

### 3. Failed Estimation — Invalid Customer
- **Given** I have submitted an estimation for a non-existent customer
- **When** the Customer Service validates
- **Then** the estimation is `REJECTED` and I see an error message indicating the customer was not found

### 4. Failed Estimation — Invalid Vehicle
- **Given** I have submitted an estimation with a vehicle ID that does not exist
- **When** the Vehicle Service validates
- **Then** the estimation is `REJECTED` and I see an error about the invalid vehicle

### 5. Estimation Timeout
- **Given** I have submitted an estimation
- **When** no terminal event arrives within 5 minutes (network issue, service down)
- **Then** the Estimation Service timeout triggers, the estimation is set to `REJECTED`, and I see a timeout message

### 6. View Estimation Status
- **Given** I have submitted an estimation
- **When** I navigate to the estimations page or estimation detail
- **Then** I see the current status (STARTED, COMPLETED, REJECTED), the premium (if completed), and any error details

### 7. List Estimations
- **Given** I am on the estimations page
- **When** the page loads
- **Then** I see a list of all estimations with customer name, insurance type, status, premium, and date, with filters by customer, status, and date range

---

## Acceptance Criteria

- Estimation starts in `STARTED` status
- SAGA completes within 5 seconds under normal conditions
- Timeout triggers after 5 minutes of no terminal event
- Idempotent consumers: duplicate events are ignored
- Failed estimation shows clear error message to the user
- All events carry `sagaId` and `traceId` for correlation
