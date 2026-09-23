# library-api

Kotlin + Spring Boot + JPA + Querydsl + MySQL 학습용 CRUD 예제 (작가 1:N 책).
설명 문서: [`../notes/kotlin-spring-jpa-querydsl-guide.md`](../notes/kotlin-spring-jpa-querydsl-guide.md)

## 실행

```bash
./gradlew test       # Docker 없이 H2(MySQL 모드)로 실행. MySQL 컨테이너 테스트는 Docker가 켜져 있을 때만
./gradlew bootRun    # Docker Desktop을 켜 두면 compose.yaml의 MySQL 8.4가 자동으로 뜬다
```

## API 예시

```bash
curl -X POST localhost:8080/api/authors -H 'Content-Type: application/json' -d '{"name":"김코틀"}'
curl -X POST localhost:8080/api/books -H 'Content-Type: application/json' \
  -d '{"title":"코틀린 입문","type":"COMPUTER","price":20000,"authorId":1}'
curl 'localhost:8080/api/books?title=코틀린&page=0&size=10'
curl localhost:8080/api/authors/1
curl -X PATCH localhost:8080/api/books/1 -H 'Content-Type: application/json' \
  -d '{"title":"코틀린 실전","type":"COMPUTER","price":25000}'
curl -X DELETE localhost:8080/api/books/1
curl localhost:8080/api/dashboard   # 코루틴으로 두 통계를 동시에 조회
```

## 구조

```
src/main/kotlin/com/example/library
├── author/   Author(1) · AuthorRepository(JPQL Projection) · Service · Controller · DTO
├── book/     Book(N) · BookRepository(인터페이스 Projection) · BookQueryRepository(Querydsl) · Service · Controller · dto/
├── dashboard/ 코루틴 예제: suspend 컨트롤러 + async로 두 조회 동시 실행
└── common/   BaseTimeEntity · JpaConfig(Auditing, JPAQueryFactory) · 예외 처리 · PageResponse · findByIdOrThrow
src/test/kotlin/com/example/library
├── learning/ Kotlin 학습용 테스트 — 여기부터 실행해 보기
│             KotlinBasicsTest(val/var · mutable · nullable · 생성자 · backing property · data class · companion)
│             KotlinIdiomsTest(스코프 함수 · 확장 함수 · sealed/when · 컬렉션 · lazy · value class …)
│             CoroutineBasicsTest(suspend · async · 구조적 동시성 · Dispatcher) · JUnitLifecycleTest(@JvmStatic)
│             InteropSample.kt + src/test/java/…/JavaInteropTest.java (Java에서 Kotlin 부르기)
├── dashboard/ DashboardServiceTest(동시 실행 실측) · CoroutineTransactionTest(트랜잭션 함정) · DashboardControllerTest
├── book/     BookTest(단위) · BookServiceMockKTest / BookServiceMockitoTest(Mock 단위)
│             BookQueryRepositoryTest(@DataJpaTest) · BookControllerWebMvcTest(@WebMvcTest)
│             BookServiceTest · BookControllerTest(@SpringBootTest)
├── author/   AuthorRelationTest (cascade, LAZY 프록시, fetch join, Projection)
└── MySqlContainerTest (Testcontainers, Docker 필요)
```
