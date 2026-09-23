plugins {
	kotlin("jvm") version "2.3.21"
	kotlin("plugin.spring") version "2.3.21" // @Component, @Transactional 등이 붙은 클래스를 open
	kotlin("plugin.jpa") version "2.3.21"    // @Entity 등에 no-arg 생성자 생성
	kotlin("kapt") version "2.3.21"          // Querydsl Q클래스 생성 (어노테이션 프로세서)
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

val querydslVersion = "7.6" // OpenFeign Querydsl: com.querydsl 5.x의 후속 포크 (Hibernate 7 / jakarta 지원)

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.kafka:spring-kafka")                     // Kafka Producer/Consumer
	implementation("org.springframework.boot:spring-boot-starter-data-redis")    // Redis (Lettuce 클라이언트)
	implementation("org.springframework.boot:spring-boot-starter-cache")         // @Cacheable
	implementation("org.springframework.boot:spring-boot-starter-actuator")      // 헬스체크 · Prometheus 메트릭
	implementation("io.micrometer:micrometer-registry-prometheus")               // /actuator/prometheus
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("tools.jackson.module:jackson-module-kotlin")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")    // 코루틴 (버전은 스프링 부트가 관리)
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor") // Spring MVC에서 suspend 컨트롤러를 쓰려면 필요

	implementation("io.github.openfeign.querydsl:querydsl-jpa:$querydslVersion")
	kapt("io.github.openfeign.querydsl:querydsl-apt:$querydslVersion:jpa")

	runtimeOnly("com.mysql:mysql-connector-j")
	developmentOnly("org.springframework.boot:spring-boot-docker-compose")

	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.springframework.kafka:spring-kafka-test")            // EmbeddedKafka (Docker 불필요)
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("io.mockk:mockk:1.14.11") // Kotlin 친화적인 Mock 라이브러리 (final 클래스·기본값 인자·코루틴 지원)
	testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test") // runTest: 가상 시간으로 코루틴 테스트
	testImplementation("org.testcontainers:testcontainers-mysql")
	testImplementation("com.redis:testcontainers-redis")                         // Redis 컨테이너 (Docker 필요)
	testRuntimeOnly("com.h2database:h2")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		// -Xjsr305=strict: 스프링의 @Nullable 등을 Kotlin 타입(null 가능/불가)으로 엄격하게 해석
		// -Xannotation-default-target=param-property: 생성자 프로퍼티의 어노테이션을 파라미터 + 필드에 함께 적용
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

// plugin.spring은 @Entity를 open하지 않는다. Entity가 final이면 Hibernate가 LAZY 프록시를 만들 수 없다.
allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
	useJUnitPlatform()
}
