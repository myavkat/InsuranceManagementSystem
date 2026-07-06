plugins {
    java
    `java-library`
    id("org.springframework.boot") version "4.0.6" apply false
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.insurancemanagementsystem"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.1.2")
        mavenBom("org.testcontainers:testcontainers-bom:2.0.5")
    }
}

dependencies {
    api(project(":common:common-message"))
    api("org.springframework.boot:spring-boot-starter-test")
    testImplementation(platform("org.testcontainers:testcontainers-bom:2.0.5"))
    api("org.testcontainers:testcontainers")
    api("org.testcontainers:testcontainers-postgresql")
    api("org.testcontainers:testcontainers-kafka")
    api("org.testcontainers:testcontainers-junit-jupiter")
    api("org.springframework.kafka:spring-kafka-test")
}

tasks.test {
    useJUnitPlatform()
}
