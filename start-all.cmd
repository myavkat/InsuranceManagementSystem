@echo off
REM ============================================================
REM  start-all.cmd
REM  Builds JARs, starts infrastructure + all 6 services as containers.
REM  Usage: start-all [-skip-build]
REM ============================================================
setlocal
set ROOT=%~dp0
cd /d "%ROOT%infra\docker"

set SKIP_BUILD=0
if /i "%~1"=="-skip-build" set SKIP_BUILD=1

if %SKIP_BUILD%==1 goto :start
echo === Building JARs ===
cd /d "%ROOT%"
call .\gradlew.bat bootJar -x test
if %ERRORLEVEL% neq 0 (echo BUILD FAILED & exit /b 1)
cd /d "%ROOT%infra\docker"

:start
echo === Starting infrastructure + services ===
docker compose -f docker-compose.yml -f docker-compose.override.yml -f docker-compose.services.yml up -d --build

echo.
echo === Done: 6 services running ===
echo   Stop:  stop-all
echo   Logs:  docker logs -f customer-service
echo.
echo Ports: 8081=customer 8082=vehicle 8083=realestate 8084=insurance 8085=estimation 8086=reference-data
endlocal
