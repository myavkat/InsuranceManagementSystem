plugins {
	java
	war
	id("org.springframework.boot") version "3.5.3"
	id("io.spring.dependency-management") version "1.1.0"
}

group = "com.sbm"
version = "0.0.1-SNAPSHOT"

java {
	sourceCompatibility = JavaVersion.VERSION_17
	targetCompatibility = JavaVersion.VERSION_17
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
	implementation("org.springframework.boot:spring-boot-starter-webflux")
	implementation("org.springframework.boot:spring-boot-starter-web")
	
	implementation("org.webjars:bootstrap:5.3.0")
	implementation("org.webjars:popper.js:2.9.3")
	implementation("org.webjars:webjars-locator:0.47")
	implementation("org.webjars:font-awesome:6.4.0")
	
	implementation("commons-validator:commons-validator:1.7")
	implementation("commons-codec:commons-codec:1.7")
	
	runtimeOnly("com.microsoft.sqlserver:mssql-jdbc")
	runtimeOnly("io.r2dbc:r2dbc-mssql")
	
	testImplementation("org.springframework.boot:spring-boot-starter-test")
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
