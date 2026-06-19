# Story: User Authentication

## Description
As a user (agent or admin), I want to register, log in, and maintain a secure session so that I can access the insurance management system.

---

## Scenarios

### 1. User Registration
- **Given** I am a new user
- **When** I navigate to `/register` and fill in my username, email, and password
- **Then** my account is created and I am redirected to `/login`

### 2. User Login
- **Given** I have a registered account
- **When** I enter valid credentials on `/login`
- **Then** I receive a JWT access token and refresh token, and I am redirected to the dashboard

### 3. Invalid Login
- **Given** I have a registered account
- **When** I enter an incorrect password 5 times
- **Then** my account is locked for 15 minutes

### 4. Token Refresh
- **Given** I have an active session
- **When** my access token expires
- **Then** the BFF automatically uses my refresh token to obtain a new access token without requiring re-login

### 5. Unauthenticated Access
- **Given** I am not logged in
- **When** I try to access any dashboard page (e.g., `/customers`)
- **Then** I am redirected to `/login`

---

## Acceptance Criteria

- Passwords are hashed with BCrypt (strength 12)
- Access tokens expire after 15 minutes
- Refresh tokens are single-use and expire after 7 days
- Maximum 5 failed login attempts before 15-minute lockout
- JWT is validated on every API call via the API Gateway
