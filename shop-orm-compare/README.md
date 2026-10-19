# shop-orm-compare

같은 쇼핑몰 로직(가입·상품·주문·취소·검색·집계)을 **JPA + QueryDSL**, **Exposed DAO**, **Exposed DSL** 세 가지로 구현하고,
하나의 MySQL(소스 + 레플리카, GTID 복제)을 함께 쓰며 비교하는 프로젝트입니다.
세 구현은 같은 API·같은 스키마·같은 계약 테스트를 공유하므로, 차이는 ORM 코드와 실제로 나가는 SQL에만 있습니다.

## 구성

세 프로젝트는 **서로 아무것도 공유하지 않는 독립 Gradle 프로젝트**다. 설정·DTO·예외·라우팅·Flyway 스크립트·테스트까지 각자 자기 언어로 갖는다.
공유하는 것은 인프라인 `docker/` 하나뿐이다.

```
docker/                  MySQL 8.4 소스(server_id=1) + 레플리카(server_id=2), GTID 복제 (세 프로젝트의 앱·테스트가 함께 사용)
jpa-querydsl/  :8081     Java · Spring Data JPA · QueryDSL 7.6(OpenFeign) · Lombok · Gradle(Groovy) · 테스트 JUnit(Java)
exposed-dao/   :8082     Kotlin · Exposed DAO (엔티티·identity map·지연 로딩) · Gradle(Kotlin DSL) · 테스트 Kotlin
exposed-dsl/   :8083     Kotlin · Exposed DSL (SQL 그대로, 불변 data class) · Gradle(Kotlin DSL) · 테스트 Kotlin
```

각 프로젝트의 패키지 구성은 같다 (`com.example.shop`).

| 패키지 | 내용 |
|---|---|
| `config` | writer/reader 라우팅 (`LazyConnectionDataSourceProxy#setReadOnlyDataSource`) |
| `common` | `ShopClock`(UTC·마이크로초), 예외, `GlobalExceptionHandler`, 페이지 응답, 정렬 화이트리스트. Exposed는 감사 컬럼·예외 번역, JPA는 `BaseTimeEntity` |
| `routing` | `/api/db/routing` (어느 서버·계정으로 붙었는지) |
| `user` · `item` · `order` | 매핑(엔티티 또는 테이블), DTO, 리포지토리, 서비스, 컨트롤러 |
| `src/main/resources/db/migration` | Flyway V1. **세 프로젝트가 바이트 단위로 같은 파일**이어야 한다 (같은 DB에 적용, 체크섬이 다르면 기동 실패) |
| `src/test/.../ShopContractTest` | 계약 테스트. 세 프로젝트가 같은 시나리오·같은 테스트 이름·같은 검증 값을 각자의 언어로 갖는다 |
| `src/test/.../support` | 테스트용 MySQL 복제(`ReplicatedMySql`), JDBC SQL 기록기(`SqlRecorder`) |

- **라우팅**: `@Transactional(readOnly = true)`면 레플리카(`shop_reader`, SELECT 전용), 아니면 소스(`shop`). `setReadOnly(true)`를 커넥션에 거는 주체만 다르다 (JPA `JpaTransactionManager`, Exposed `SpringTransactionManager`).
- **복제**: 레플리카는 `super_read_only`. 소스 초기화 이력은 `RESET BINARY LOGS AND GTIDS`로 비우고, 이후 Flyway DDL과 애플리케이션 쓰기만 복제된다.
- **스키마 검증**: JPA는 기동 시 `ddl-auto=validate`, Exposed는 계약 테스트의 `MigrationUtils` 비교.

## 실행

```bash
docker compose -f docker/compose.yaml up -d        # 소스 :3307, 레플리카 :3308 (포트는 SOURCE_PORT/REPLICA_PORT로 변경)
cd jpa-querydsl && ./gradlew bootRun               # :8081  (세 앱을 동시에 띄워도 된다. Flyway는 DB 락으로 한 번만 적용)
cd exposed-dao  && ./gradlew bootRun               # :8082
cd exposed-dsl  && ./gradlew bootRun               # :8083

curl localhost:8081/api/db/routing                 # write → server 1 / shop, readOnly → server 2 / shop_reader
docker compose -f docker/compose.yaml exec mysql-replica mysql -uroot -proot -e "SHOW REPLICA STATUS\G"
docker compose -f docker/compose.yaml down -v      # V1을 바꿨거나 초기화 스크립트를 다시 돌리려면 볼륨까지 삭제
```

같은 DB를 쓰므로 한 구현으로 쓴 데이터를 다른 구현으로 읽을 수 있다 (예: 8081로 가입 → 8082로 상품 → 8083으로 주문 → 셋 모두에서 같은 응답).

## API (세 구현 동일)

| 메서드 | 경로 | 내용 |
|---|---|---|
| POST | `/api/users` | 가입. 이메일 중복 409 |
| GET | `/api/users/{id}` | 조회 (레플리카) |
| POST | `/api/items` | 상품 생성 |
| GET | `/api/items?keyword=&minPrice=&maxPrice=&page=&size=&sort=` | 동적 조건 검색, 정렬 키 `id·name·price·createdAt`만 허용(그 외 400) |
| PATCH | `/api/items/{id}` | 낙관적 락 수정 (`version` 불일치 409) |
| GET | `/api/items/best-sellers?from=&to=&limit=` | 기간 판매 집계 (반개구간, PLACED만) |
| POST | `/api/orders` | 주문: 상품 행 FOR UPDATE → 재고 차감 → 저장 |
| POST | `/api/orders/{id}/cancel` | 취소: 주문 행 FOR UPDATE → 재고 원자적 복구 |
| GET | `/api/orders/{id}` · `/api/orders?userId=&status=&sort=` | 주문 조회 (N:1 사용자, 1:N 주문상품 조립) |
| GET | `/api/users/{id}/orders?cursor=&size=` | 커서(keyset) 목록 |
| GET | `/api/db/routing` | 쓰기·읽기 트랜잭션이 붙은 서버 |

## 테스트

```bash
cd jpa-querydsl && ./gradlew test     # Docker 필요. 프로젝트마다 MySQL 소스+레플리카 컨테이너를 띄워 계약 테스트 실행
cd exposed-dao  && ./gradlew test
cd exposed-dsl  && ./gradlew test
```

계약 테스트(`ShopContractTest`)는 JPA 15개(Java), DAO·DSL 16개(Kotlin, 스키마 검증 1개 추가)다. 나머지 15개는 이름·순서·검증 값이 같다.
라우팅(server_id·계정), 복제, 중복 가입, 동적 검색·이스케이프, 정렬 화이트리스트, 낙관적 락(응답의 새 version 포함),
주문 실패 시 롤백, **동시 주문 100건 → 재고 50건만 성공**, 동시 취소, N+1 없음(페이지 크기와 SQL 수 무관), 커서, 반개구간 집계, HTTP 오류 형식을 검증한다.
쓰기 뒤 레플리카 조회 전에는 `WAIT_FOR_EXECUTED_GTID_SET`으로 복제를 기다린다.

`sqlReport` 테스트가 JDBC 수준에서 실제 실행된 SQL을 `<프로젝트>/build/sql-report/<구현>.md`에 남긴다.

### 같은 요청의 SQL 수 (측정)

| 요청 | JPA + QueryDSL | Exposed DAO | Exposed DSL | 차이의 원인 |
|---|---|---|---|---|
| 상품 수정 (낙관적 락) | 2 | 2 | 2 | JPA는 조회 후 flush UPDATE `WHERE version = ?`, DAO는 FOR UPDATE 후 flush, DSL은 조건 UPDATE 후 재조회 |
| 주문 (상품 2종) | 7 | 6 | 8 | JPA는 IDENTITY라 order_items를 **행마다** INSERT, 재고 UPDATE는 전체 컬럼. DAO는 재고 UPDATE 2건을 JDBC 배치 1회로. DSL은 상품마다 UPDATE + 응답용 재조회 2 |
| 주문 취소 | 7 | 7 | 7 | 셋 다 주문 잠금 → 상태 → 재고 `stock + ?` × 2 → 응답 조립 |
| 주문 단건 조회 | 2 | 3 | 2 | JPA fetch join + 배치 IN, DSL JOIN + IN, DAO는 user·items를 각각 IN |
| 주문 검색 (4건 페이지) | 3 | 4 | 3 | 페이지 + COUNT + 1:N IN. DAO는 N:1도 IN 한 번 더 |
| 커서 (3건) | 2 | 3 | 2 | 위와 같은 이유 |

## 구현별로 볼 곳

| 주제 | jpa-querydsl | exposed-dao | exposed-dsl |
|---|---|---|---|
| 동적 조건·정렬 | `item/ItemQueryRepository` (BooleanExpression null 무시) | `item/ItemRepository.search` | `item/ItemRepository.search` (`listOfNotNull<Op<Boolean>>`) |
| 비관적 락 | `@Lock(PESSIMISTIC_WRITE)` | `find {}.forUpdate()` | `.forUpdate(ForUpdateOption.ForUpdate)` |
| 낙관적 락 | `@Version` + 응답 전 `flush()` | FOR UPDATE + version 비교 (flush UPDATE에 조건을 못 붙임) | `WHERE version = ?` + 영향 행 수 |
| 원자적 증가 | `@Modifying(flushAutomatically, clearAutomatically)` JPQL, version·updatedAt 직접 | 그 한 줄만 DSL | `stock + ?` |
| 감사 컬럼 | `@CreatedDate`/`@LastModifiedDate` (벌크 UPDATE는 미적용) | `Entity.flush()` 오버라이드 | `clientDefault` + `updateAudited` (일반 update·upsert·replace는 컴파일 에러) |
| 예외 번역 | Spring 기본 | `@Repository` 끝에서 `flushCache()` | `@Repository` 번역 인터셉터 (`setBeforeExistingAdvisors(false)`) |
| 중복 가입 | `saveAndFlush` + `DataIntegrityViolationException` (트랜잭션은 rollback-only) | `new {}` + `flushCache()` + `DuplicateKeyException` | 일반 `insert` + `DuplicateKeyException` (insertIgnore는 FK 위반도 삼켜 쓰지 않음) |
| 집계 | QueryDSL `sumLong()` (7.x에서 `sum()` 이름이 바뀜) | DAO에 없음 → DSL | DSL |
| timeout | 트랜잭션 전체 5초 | SQL 문장당 5초 | SQL 문장당 5초 |

## 세 프로젝트가 지키는 같은 규칙

- 시각은 `ShopClock.now()`(UTC, 마이크로초 절사) 하나로. JVM은 `-Duser.timezone=UTC`.
- 쓰기 응답은 같은 쓰기 트랜잭션(소스) 안에서 만든다. 쓰기 직후 다른 요청의 조회는 레플리카 지연을 겪을 수 있다.
- 상품 `version`은 행이 바뀔 때마다 +1 (정보 수정, 재고 차감, 재고 복구). JPA `@Version`의 기본 동작에 세 구현을 맞췄다.
