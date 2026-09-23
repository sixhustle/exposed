# 1단계: 빌드 (의존성 캐시를 위해 소스보다 먼저 gradle 파일만 복사)
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies || true
COPY src src
RUN ./gradlew --no-daemon clean bootJar -x test

# 2단계: 실행 (JDK 대신 JRE, root가 아닌 사용자)
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd -r -u 1001 app
COPY --from=build /workspace/build/libs/*.jar app.jar
USER 1001
EXPOSE 8080
# 컨테이너 메모리에 맞춰 힙을 자동 계산한다 (-XX:MaxRAMPercentage)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/app.jar"]
