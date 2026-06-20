rootProject.name = "insurance-management-system"

include(
    "common:common-message",
    "common:common-test",
    "services:auth-service",
    "services:customer-service",
    "services:vehicle-service",
    "services:realestate-service",
    "services:insurance-service",
    "services:estimation-service",
    "services:reference-data-service",
    "services:api-gateway",
    "services:reference-skeleton"
)
