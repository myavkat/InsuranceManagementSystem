plugins {
	java
	war
	id("org.springframework.boot") version "4.0.6"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.sbm"
version = "0.0.1-SNAPSHOT"

java {
	sourceCompatibility = JavaVersion.VERSION_25
	targetCompatibility = JavaVersion.VERSION_25
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
	implementation("org.springframework.boot:spring-boot-starter-webflux")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	
	implementation("org.webjars:bootstrap:5.3.8")
	implementation("org.webjars:popper.js:2.9.3")
	implementation("org.webjars:webjars-locator-lite")
	implementation("org.webjars:font-awesome:6.4.0")
	
	implementation("commons-validator:commons-validator:1.7")
	implementation("commons-codec:commons-codec:1.7")
	implementation("org.apache.commons:commons-lang3:3.20.0")
	
	runtimeOnly("com.microsoft.sqlserver:mssql-jdbc")
	runtimeOnly("io.r2dbc:r2dbc-mssql")
	
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("io.projectreactor:reactor-test")
	
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	
	compileOnly("org.webjars.npm:izitoast:1.4.0")
	compileOnly("org.projectlombok:lombok")
	
	annotationProcessor("org.projectlombok:lombok")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.named<War>("war") {
  enabled = false
}
