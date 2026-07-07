@echo off
REM Stop all infrastructure + services
cd /d "%~dp0infra\docker"
docker compose -f docker-compose.yml -f docker-compose.override.yml -f docker-compose.services.yml down
echo All services and infrastructure stopped.
