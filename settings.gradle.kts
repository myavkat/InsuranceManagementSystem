rootProject.name = "insurance-management-system"

include("common:common-message")
include("common:common-web")

// Future services (uncomment when build files are created):
include("common:common-test")
// include("services:auth-service")
include("services:customer-service")
include("services:vehicle-service")
include("services:realestate-service")
include("services:insurance-service")
include("services:estimation-service")
include("services:reference-data-service")
include("services:eureka-server")
// include("services:api-gateway")

plugins {
    // dependency analysis plugin (https://github.com/autonomousapps/dependency-analysis-gradle-plugin)
    id("com.autonomousapps.build-health") version "3.15.0"
}
