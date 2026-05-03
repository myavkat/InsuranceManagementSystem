# AGENTS.md - InsuranceManagementSystem

## Project Structure

- `backend/` - Java Spring Boot WebFlux application (Gradle, Java 25)
- `frontend/` - Vue 3 + Vite + TypeScript + TailwindCSS 4

## Developer Commands

### Frontend
```bash
npm run dev                # Start dev server
npm run build              # Type-check + build for production
npm run type-check        # Run vue-tsc
npm run lint              # Run oxlint + eslint (fixes: npm run lint -- --fix)
npm run test:unit         # Run Vitest unit tests
npm run test:e2e          # Run Playwright e2e tests (requires: npx playwright install)
```

### Backend
```bash
gradle clean build        # Build application
gradle bootRun           # Run dev server
```

## Order Matters

Frontend: `lint -> type-check -> test:unit` before committing.

## Environment Quirks

- Backend uses Spring Boot 4.0.6 with Java 25 target (source/targetCompatibility = JavaVersion.VERSION_25)
- Backend uses R2dbc + MSSQL for database
- Frontend uses TailwindCSS 4.x (Vite plugin: `@tailwindcss/vite`)
- Frontend Node version: `^20.19.0 || >=22.12.0`
- No custom CSS files; all styling via TailwindCSS utility classes