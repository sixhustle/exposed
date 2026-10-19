plugins {
	kotlin("jvm") version "2.3.21"
	kotlin("plugin.spring") version "2.3.21"
	id("org.springframework.boot") version "4.1.1"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

val exposedVersion = "1.5.1"

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-data-commons") // Pageable/Page + 웹 바인딩 (JPA 없이 spring-data-commons만)
	implementation("org.flywaydb:flyway-mysql")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("tools.jackson.module:jackson-module-kotlin")

	// Exposed: SpringTransactionManager 자동 구성 + exposed-core/jdbc/dao
	implementation("org.jetbrains.exposed:exposed-spring-boot4-starter:$exposedVersion")
	implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
	implementation("org.jetbrains.exposed:exposed-migration-jdbc:$exposedVersion") // 테이블 정의 ↔ 실제 스키마 비교 (테스트)

	runtimeOnly("com.mysql:mysql-connector-j")

	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-mysql")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
	jvmArgs("-Duser.timezone=UTC") // 세 프로젝트가 같은 DB에 같은 시각을 쓰도록 JVM 타임존 고정
	// 테스트용 MySQL 소스·레플리카가 ../docker 의 설정·초기화 스크립트를 그대로 쓴다
	systemProperty("compare.docker-dir", file("../docker").absolutePath)
}

tasks.bootRun {
	jvmArgs("-Duser.timezone=UTC")
}
