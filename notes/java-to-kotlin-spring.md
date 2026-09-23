# Spring Boot Java → Kotlin 전환 핵심 정리

> 출처: 인프런 「실전! 코틀린과 스프링 부트로 도서관리 애플리케이션 개발하기」 Section 1~6 PDF
> 비교 코드: `Kotlin_Spring_Library_Application/library-app.zip` (Java 원본) ↔ `~/Downloads/library-app` (Kotlin 완성본)
> 강의 기준 버전: Spring Boot 2.6.8 · Kotlin 1.6.21 · JDK 11 · `javax.persistence`

---

## 0. 한눈에 보기

| 문제 상황 | 해결 |
|---|---|
| Kotlin 코드를 컴파일할 수 없음 | `kotlin("jvm")` 플러그인 + `kotlin-stdlib-jdk8` + `src/main/kotlin` 소스 루트 |
| `Class 'X' should have [public, protected] no-arg constructor` | `kotlin("plugin.jpa")` (no-arg 플러그인) |
| `Methods annotated with '@Transactional' must be overridable` | `kotlin("plugin.spring")` (all-open 플러그인) |
| `NoClassDefFoundError: kotlin/reflect/full/KClasses` | `kotlin-reflect` 의존성 |
| `Cannot construct instance of ...Request (no Creator)` | `jackson-module-kotlin` 의존성 |
| Java getter 값이 null인데 `must not be null` 에러 | 플랫폼 타입 → Java 쪽에 `@Nullable`/`@NotNull` 붙이기 |
| `Optional<T>` 처리 | `T?` 반환 + `?:` 엘비스 연산자 + `findByIdOrNull` |
| Boolean 필드 `isXxx`가 JSON에서 `xxx`로 나감 | `@get:JsonProperty("isXxx")` |
| 어노테이션 배열 인자 `cascade = CascadeType.ALL` 컴파일 에러 | `cascade = [CascadeType.ALL]` |
| Querydsl Q클래스가 생성되지 않음 | `kotlin("kapt")` + `kapt("com.querydsl:querydsl-apt:5.0.0:jpa")` |

---

## 1. 전환 전략: 테스트 먼저, 계층은 아래에서 위로

### 1-1. 테스트부터 작성한다 (Section 1)
- **리팩토링 전에 기존 Java 코드에 대한 테스트를 Kotlin으로 먼저 작성**한다. 테스트가 동작을 보장하므로 안심하고 교체할 수 있고, 동시에 Kotlin 문법에도 익숙해진다.
- 한 계층만 테스트한다면 **Service 계층**이 가장 효율적이다(입력 대비 결과와 비즈니스 로직을 함께 검증할 수 있다).

### 1-2. 전환 순서 (Section 2, 11강)

```
Domain(Entity) → Repository → Service → DTO → Controller → Application(main)
```

| 순서 | 계층 | 특징 |
|---|---|---|
| 1 | Domain | POJO이면서 JPA Entity. 다른 Bean에 의존하지 않는다 |
| 2 | Repository | Spring Bean이며 다른 Bean에 의존하지 않는다 |
| 3 | Service | Spring Bean이며 다른 Bean에 의존한다. 비즈니스 로직이 가장 많다 |
| 4 | DTO / Controller | DTO는 단순하지만 개수가 많고, Controller는 다른 Bean에 의존한다 |

### 1-3. 교체 방법 3가지 (섞어서 사용)
1. **기존 클래스 이름 변경 후 병행**: `Book.java`를 `JavaBook`으로 바꾸고(Shift+F6) 같은 패키지에 `Book.kt`를 만든 뒤 참조를 하나씩 옮긴다. Entity처럼 참조가 많은 클래스에 적합하다.
2. **새로 만들어 한 번에 교체**: 패키지와 이름이 같은 `.kt`를 만들고 `.java`를 삭제한다. Repository처럼 단순한 클래스에 적합하다.
3. **IntelliJ `Convert Java File to Kotlin File`**: 패키지 단위로 자동 변환한 뒤 **반드시 결과를 검토**한다. DTO처럼 개수가 많은 클래스에 적합하다.
   - 자동 변환이 `Integer age`를 `age: Int`(non-null)로 바꾸는 식의 오류가 생기므로, `Int?`로 직접 고쳐야 한다.

> 컴파일 에러를 모두 찾지 못해도 괜찮다. **전체 테스트를 돌려서 깨지는 곳을 따라가며** 고치면 된다.

---

## 2. Gradle 설정

```groovy
plugins {
    id 'org.springframework.boot' version '2.6.8'
    id 'io.spring.dependency-management' version '1.0.11.RELEASE'
    id 'java'
    id 'org.jetbrains.kotlin.jvm' version '1.6.21'          // Kotlin 컴파일
    id 'org.jetbrains.kotlin.plugin.jpa' version '1.6.21'   // @Entity 등에 기본 생성자 자동 생성
    id 'org.jetbrains.kotlin.plugin.spring' version '1.6.21'// @Component/@Transactional 등 클래스 open
    id 'org.jetbrains.kotlin.kapt' version '1.6.21'         // Querydsl Q클래스 생성
}

dependencies {
    implementation 'org.jetbrains.kotlin:kotlin-stdlib-jdk8'
    implementation 'org.jetbrains.kotlin:kotlin-reflect:1.6.21'                  // 스프링의 Kotlin 리플렉션
    implementation 'com.fasterxml.jackson.module:jackson-module-kotlin:2.13.3'   // DTO JSON 역직렬화
    implementation 'com.querydsl:querydsl-jpa:5.0.0'
    kapt("com.querydsl:querydsl-apt:5.0.0:jpa")
    kapt("org.springframework.boot:spring-boot-configuration-processor")
    // ... 기존 spring-boot-starter-web / data-jpa / h2 / test
}

compileKotlin     { kotlinOptions { jvmTarget = "11" } }
compileTestKotlin { kotlinOptions { jvmTarget = "11" } }
```

- `src/main/kotlin`과 `src/test/kotlin` 디렉토리를 만들고, Java와 **같은 패키지**(`com.group.libraryapp`)를 만든다.
  - IntelliJ에서 Mark Directory as → Source Root / Test Source Root로 지정한다.
- 플러그인을 추가한 뒤에는 **Gradle refresh**를 하고, 필요하면 IntelliJ를 재시작한다.

### 왜 필요한가
| 플러그인/의존성 | 이유 |
|---|---|
| `plugin.jpa` | JPA는 인자 없는 기본 생성자가 필요하다. Kotlin의 주 생성자는 프로퍼티를 받으므로 기본 생성자가 없다 |
| `plugin.spring` | Kotlin의 클래스와 메소드는 **기본이 `final`**이다. Spring AOP 프록시(`@Transactional` 등)는 상속과 오버라이드가 필요하다 |
| `kotlin-reflect` | Spring이 Kotlin 클래스를 리플렉션으로 다룰 때 필요하다 |
| `jackson-module-kotlin` | 기본 생성자와 setter가 없는 Kotlin 클래스를 Jackson이 생성할 수 있게 해준다 |

---

## 3. Domain (JPA Entity) 전환

### Java → Kotlin 비교

```java
// Java
@Entity
public class Book {
  @Id @GeneratedValue(strategy = IDENTITY)
  private Long id;
  @Column(nullable = false)
  private String name;

  public Book() {}
  public Book(String name) {
    if (name.isBlank()) throw new IllegalArgumentException("이름은 비어 있을 수 없습니다");
    this.name = name;
  }
  public String getName() { return name; }
}
```

```kotlin
// Kotlin
@Entity
class Book(
  val name: String,                 // NOT NULL → String
  @Enumerated(EnumType.STRING)
  val type: BookType,
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long? = null,             // 저장 전에는 null → Long? + 기본값 null
) {
  init {                            // 생성 시점 검증은 init 블록에서
    if (name.isBlank()) {
      throw IllegalArgumentException("이름은 비어 있을 수 없습니다")
    }
  }
}
```

### 변환 체크리스트
- **불변이면 `val`, 변경되면 `var`**: `User.name`은 수정 기능이 있으므로 `var`, `age`는 `val`이다.
- **nullable 여부**: `@Column(nullable = false)`는 `String`, 선택값은 `Int?`, `id`는 `Long? = null`이다.
- **기본값(default parameter)**으로 Java의 오버로딩 생성자를 대체한다: `var status: UserLoanStatus = UserLoanStatus.LOANED`
- **어노테이션 배열 인자는 `[ ]`로 감싼다**: `@OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], orphanRemoval = true)`
- **컬렉션은 `MutableList`로 선언한다**: `add()`를 써야 하기 때문이다. `val userLoanHistories: MutableList<UserLoanHistory> = mutableListOf()`
- **Stream 대신 컬렉션 함수를 쓴다**:
  ```java
  this.userLoanHistories.stream().filter(h -> h.getBookName().equals(bookName)).findFirst().orElseThrow().doReturn();
  ```
  ```kotlin
  this.userLoanHistories.first { it.bookName == bookName }.doReturn()
  ```

### Kotlin + JPA 이야기거리 3가지 (14강)
1. **setter 노출**
   - 생성자의 `var` 프로퍼티는 public setter가 열려 있다. 외부에서는 `updateName()`처럼 **의미 있는 함수를 쓰는 것**이 좋다.
   - setter를 막는 방법은 두 가지다.
     ```kotlin
     // 방법 1) backing property
     class User(private var _name: String) {
       val name: String get() = _name
     }
     // 방법 2) private setter
     class User(name: String) {
       var name = name
         private set
     }
     ```
   - 프로퍼티가 많으면 번거롭다. 강사는 **setter는 열어 두되 쓰지 않는 팀 컨벤션**을 선호한다.
2. **프로퍼티 위치(생성자 안 vs 클래스 body)**: 어느 쪽이든 동작한다. 모두 생성자에 넣거나, 명확한 기준을 정해서 팀이 통일하는 것이 중요하다.
3. **Entity에는 `data class`를 쓰지 않는다**: 자동 생성되는 `equals/hashCode/toString`이 양방향 연관관계(User ↔ UserLoanHistory)에서 서로를 호출해 **무한 순환**을 일으킬 수 있다.

> TIP: `class User constructor(...)`처럼 `constructor` 키워드를 명시하면 "Find Usages"로 **객체가 생성되는 곳을 추적하기 쉽다**.

### Enum 활용 (Section 3)
- 문자열 타입(`type: String`)은 ① 잘못된 값을 막을 수 없고 ② 가능한 값을 코드만 보고 알 수 없으며 ③ 분기 로직이 `when`과 `else`로 흩어진다. **enum class**를 쓰면 세 문제가 모두 해결된다.
  ```kotlin
  enum class BookType(val score: Int) { COMPUTER(10), ECONOMY(8), SOCIETY(5), LANGUAGE(5), SCIENCE(5) }
  fun getEventScore() = this.type.score   // when 분기 없이 처리
  ```
- **`@Enumerated(EnumType.STRING)`을 반드시 붙인다.** 기본값(ORDINAL)은 순서 숫자로 저장되므로 enum 순서를 바꾸거나 값을 추가하면 데이터가 깨진다.
- **여러 Boolean 플래그 대신 enum을 쓴다.** `isActive`와 `isDeleted`를 함께 두면 2^n개 조합 중 의미 없는 상태가 생긴다. `isReturn: Boolean`은 `status: UserLoanStatus { LOANED, RETURNED }`로 바꾼다.
- Boolean 값이 여전히 필요하면 **custom getter**로 도메인에 로직을 둔다.
  ```kotlin
  val isReturn: Boolean
    get() = this.status == UserLoanStatus.RETURNED
  ```

---

## 4. Repository 전환

```java
// Java
public interface UserRepository extends JpaRepository<User, Long> {
  Optional<User> findByName(String name);
}
```
```kotlin
// Kotlin: Optional 대신 nullable 반환
interface UserRepository : JpaRepository<User, Long> {
  fun findByName(name: String): User?
}
```

- 처음에는 `Optional<User>`를 유지해 Service 변경을 최소화하고, Service까지 Kotlin으로 옮긴 뒤 `User?`로 바꾼다.
- **주의**: Spring Data 메소드 이름(`findByBookNameAndIsReturn`)은 필드명을 문자열처럼 참조한다. 필드명을 바꿔도(`isReturn`→`status`) **IDE가 빨간 줄을 띄우지 않고, 컨텍스트 로딩 시점에 실패한다**(`PropertyReferenceException`). 이 문제는 Querydsl로 해결한다(§8).

---

## 5. Service 전환

```kotlin
@Service
class UserService(
  private val userRepository: UserRepository,   // 주 생성자 = 생성자 주입 (@Autowired 불필요)
) {

  @Transactional
  fun saveUser(request: UserCreateRequest) {
    userRepository.save(User(request.name, request.age))
  }

  @Transactional(readOnly = true)
  fun getUsers(): List<UserResponse> {
    return userRepository.findAll().map(UserResponse::of)    // stream().map().collect() 대체
  }

  @Transactional
  fun updateUserName(request: UserUpdateRequest) {
    val user = userRepository.findByIdOrThrow(request.id)     // 직접 만든 확장 함수
    user.updateName(request.name)
  }

  @Transactional
  fun deleteUser(name: String) {
    val user = userRepository.findByName(name) ?: fail()
    userRepository.delete(user)
  }
}
```

### 핵심 포인트
- **생성자 레퍼런스**: Java의 `UserResponse::new`는 Kotlin에서 `::UserResponse`(생성자) 또는 `UserResponse::of`(companion 함수)이다.
- **Optional 제거**: `.orElseThrow(IllegalArgumentException::new)`는 `?: throw IllegalArgumentException()`으로 바꾼다.
- **예외 처리를 함수로 모은다.** 반환 타입이 `Nothing`이면 "정상 종료되지 않는 함수"라는 뜻이다.
  ```kotlin
  // util/ExceptionUtils.kt
  fun fail(): Nothing {
    throw IllegalArgumentException()
  }
  ```
- **`findById`의 Optional은 확장 함수로 해결한다.**
  - Spring이 이미 `CrudRepositoryExtensions.kt`에 `findByIdOrNull()`을 제공한다(`org.springframework.data.repository.findByIdOrNull`).
  - 여기서 한 단계 더 나가면, 모든 Repository에서 쓸 수 있는 **non-null 반환** 버전을 만들 수 있다.
  ```kotlin
  fun <T, ID> CrudRepository<T, ID>.findByIdOrThrow(id: ID): T {
    return this.findByIdOrNull(id) ?: fail()
  }
  ```

---

## 6. DTO 전환

```java
// Java: 필드 + 생성자 + getter
public class UserResponse {
  private final long id; private final String name; private final Integer age;
  public UserResponse(User user) { this.id = user.getId(); ... }
  // getters...
}
```
```kotlin
// Kotlin: data class + 정적 팩토리
data class UserResponse(
  val id: Long,
  val name: String,
  val age: Int?,
) {
  companion object {
    fun of(user: User): UserResponse {
      return UserResponse(
        id = user.id!!,        // 저장된 Entity이므로 id가 있음을 단언
        name = user.name,
        age = user.age,
      )
    }
  }
}
```

- **DTO에는 `data class`가 적합하다**(Entity와 반대). equals, hashCode, toString이 언제든 쓰일 수 있기 때문이다.
- 권장 순서: `init` 블록 대입 < 부생성자(`constructor(user: User): this(...)`) < **정적 팩토리(`companion object { fun of() }`)**
- Entity → DTO 매핑은 DTO의 `of()`가 맡는다. **Service는 트랜잭션과 도메인 조회·제어에만 집중**한다.
- 관련 있는 작은 클래스는 **한 파일에 여러 개** 둘 수 있다(`UserLoanHistoryResponse` + `BookHistoryResponse`).
- `companion object`는 컨벤션상 클래스의 **맨 아래**에 둔다.

### Jackson + Boolean `is` 접두사 함정 (Section 4 부록)
- Jackson은 getter 이름으로 JSON 키를 정한다. `isReturn(): Boolean`은 `"return"`으로 나간다(Java와 Lombok에서도 동일).
- Kotlin의 `val isReturn`은 생성자 파라미터, 필드, getter 중 어디에나 해당할 수 있다. 그래서 **use-site target `@get:`**으로 적용 대상을 명시해야 한다.
  ```kotlin
  data class BookHistoryResponse(
    val name: String,
    @get:JsonProperty("isReturn")
    val isReturn: Boolean,
  )
  ```
- 특정 필드를 JSON에서 제외하려면 `@JsonIgnore`를 쓴다.

---

## 7. Controller & Application 전환

```kotlin
@RestController
class UserController(
  private val userService: UserService,
) {
  @GetMapping("/user")
  fun getUsers(): List<UserResponse> = userService.getUsers()

  @DeleteMapping("/user")
  fun deleteUser(@RequestParam name: String) {   // String → 필수 파라미터
    userService.deleteUser(name)
  }
}
```
- **`@RequestParam`의 필수 여부는 nullable 타입으로 결정된다.** `name: String`이면 필수이고, `name: String?`이면 `required = false` 없이도 선택 파라미터가 된다.

```kotlin
@SpringBootApplication
class LibraryAppApplication

fun main(args: Array<String>) {                 // top-level 함수 = Java static main
  runApplication<LibraryAppApplication>(*args)  // Spring의 Kotlin용 inline 함수, *args = spread 연산자
}
```

---

## 8. Querydsl (Section 6)

### 왜 필요한가: JPQL(`@Query`)과 Spring Data 메소드 이름의 단점
1. 쿼리를 문자열로 쓰므로 버그를 찾기 어렵다
2. 문법이 SQL과 달라서 매번 찾아봐야 한다
3. **동적 쿼리 작성이 어렵다**. 선택 조건이 4개면 `findByA…` 메소드가 16개 필요하다
4. 도메인 코드 변경에 취약하다
5. 메소드 이름에 제약이 있다

### 설정
- `kapt`와 `querydsl-apt`로 빌드하면 `QUser`, `QBook` 같은 **Q클래스**가 생성된다.
- `JPAQueryFactory`를 Bean으로 등록한다.
```kotlin
@Configuration
class QuerydslConfig(private val em: EntityManager) {
  @Bean
  fun querydsl(): JPAQueryFactory = JPAQueryFactory(em)
}
```

### 사용 방법 2가지

| | 방법 1: Custom 인터페이스 | 방법 2: 별도 `@Component` 클래스 |
|---|---|---|
| 구조 | `UserRepository : JpaRepository, UserRepositoryCustom` + `UserRepositoryCustomImpl` | `BookQuerydslRepository(queryFactory)` |
| 장점 | Service는 Repository 하나만 쓰면 된다 | 클래스 하나로 끝나서 간결하다 |
| 단점 | 인터페이스와 구현체를 항상 함께 만들어야 한다 | Service가 두 Repository를 모두 주입받아야 한다 |
| 강사 선호 | | ✅ 멀티 모듈 환경에서 장점이 크다 |

### Kotlin다운 동적 쿼리
```kotlin
fun find(bookName: String, status: UserLoanStatus? = null): UserLoanHistory? {
  return queryFactory.select(userLoanHistory)
    .from(userLoanHistory)
    .where(
      userLoanHistory.bookName.eq(bookName),
      status?.let { userLoanHistory.status.eq(status) },  // null이면 Querydsl이 조건을 무시, 여러 조건은 AND
    )
    .limit(1)
    .fetchOne()
}

fun count(status: UserLoanStatus): Long {
  return queryFactory.select(userLoanHistory.count())
    .from(userLoanHistory)
    .where(userLoanHistory.status.eq(status))
    .fetchOne() ?: 0L
}
```
- **default parameter와 `?.let`을 조합**하면 메소드 하나로 `findByBookName`과 `findByBookNameAndStatus`를 모두 대체할 수 있다.
- DTO로 바로 조회하려면 `Projections.constructor(BookStatResponse::class.java, book.type, book.id.count())`를 쓴다. `count()`의 결과는 **Long**이다.

---

## 9. 성능 & 쿼리 (Section 4~5, Kotlin과 무관하지만 함께 알아두기)

- **N+1 문제**
  - 원인: `findAll()`로 User N명을 조회한 뒤 각 `user.userLoanHistories`에 접근하면 Lazy 프록시가 초기화되면서 쿼리가 N번 더 나간다.
  - 해결: **fetch join**을 쓴다.
    ```kotlin
    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.userLoanHistories")
    fun findAllWithHistories(): List<User>
    ```
  - `LEFT`는 대출 기록이 없는 유저도 포함하기 위해, `DISTINCT`는 join으로 중복된 User 행을 제거하기 위해, `FETCH`는 연관 객체까지 한 번에 로딩하기 위해 쓴다.
- **애플리케이션 계산 vs DB 계산**
  - `findAll().size`나 `groupBy {}`는 모든 데이터를 메모리로 가져온다.
  - `countByStatus`, `GROUP BY` 쿼리는 DB가 계산하므로 전송량이 적다.
  - 데이터가 많으면 DB에서 계산하고, 그보다 더 많으면 배치나 메시지 큐 같은 아키텍처를 고려한다.
- **컬렉션 함수로 가변 상태를 없앤다**
  ```kotlin
  // mutableListOf + for + var count++ 대신
  bookRepository.findAll()
    .groupBy { it.type }
    .map { (type, books) -> BookStatResponse(type, books.size.toLong()) }
  ```

---

## 10. 테스트 코드 (Kotlin + JUnit5)

```kotlin
@SpringBootTest
class UserServiceTest @Autowired constructor(   // 테스트 클래스는 생성자에 @Autowired를 명시해야 한다
  private val userService: UserService,
  private val userRepository: UserRepository,
) {

  @AfterEach
  fun clean() {
    userRepository.deleteAll()   // 테스트끼리 H2 DB를 공유하므로 매번 정리
  }

  @Test
  @DisplayName("유저 삭제가 정상 동작한다")
  fun deleteUserTest() {
    // given
    userRepository.save(User("A", null))
    // when
    userService.deleteUser("A")
    // then
    assertThat(userRepository.findAll()).isEmpty()
  }
}
```

| 포인트 | 내용 |
|---|---|
| `@BeforeAll` / `@AfterAll` | `companion object` 안에 두고 **`@JvmStatic`**을 붙인다 |
| 예외 검증 | `assertThrows<IllegalArgumentException> { ... }.apply { assertThat(message).isEqualTo("...") }` |
| 컬렉션 검증 | `hasSize`, `extracting("name").containsExactlyInAnyOrder(...)`, `isEmpty` |
| 플랫폼 타입 | Java의 `Integer getAge()`는 Kotlin이 nullable 여부를 모른다. null이 들어오면 `must not be null` 에러가 난다. Java 쪽에 `org.jetbrains.annotations.@Nullable`/`@NotNull`을 붙인다 |
| getter 변환 경고 | IntelliJ가 `userService.getUsers()`를 `userService.users`로 바꾸라고 제안하지만, 단순 getter가 아니므로 무시한다 |
| **Test Fixture** | Entity에 `companion object { fun fixture(name = "책 이름", type = COMPUTER, id = null) }`를 둔다. **필드가 추가되어도 테스트를 고칠 필요가 없다**(Object Mother 패턴) |
| 테스트 설계 | 복잡한 테스트 1개보다 **단순한 테스트 여러 개**가 낫다. "LOANED 2개 + RETURNED 1개"처럼 **변환 버그를 잡을 수 있는 데이터**를 고른다 |

---

## 11. 완성본 코드에서 발견한 차이점 ⚠️

강의 PDF의 설명과 `~/Downloads/library-app` 완성본 코드가 다른 부분이다. 직접 구현할 때 참고하자.

1. **`UserLoanHistory.status`에 `@Enumerated(EnumType.STRING)`이 빠져 있다.** PDF 23강에서는 붙이는데, 완성본에는 없어서 ORDINAL(숫자)로 저장된다.
2. **`BookHistoryResponse.isReturn`에 `@get:JsonProperty("isReturn")`이 빠져 있다.** 이대로면 응답 JSON 키가 `"return"`이 되어 v2 화면의 대여 상태가 표시되지 않는다(Section 4 부록의 내용).
3. **Querydsl fetch join 형태.** 완성본은 `.leftJoin(userLoanHistory).on(userLoanHistory.user.id.eq(user.id)).fetchJoin()`처럼 `on`을 쓰는 엔티티 조인이다. fetch join은 보통 **연관관계 경로로 조인할 때** 적용되므로 아래 형태가 더 확실하다. 실제로 쿼리가 1번만 나가는지 `show_sql` 로그로 확인해보자.
   ```kotlin
   .leftJoin(user.userLoanHistories, userLoanHistory).fetchJoin()
   ```

---

## 12. 지금(2026) 새 프로젝트에 적용할 때 달라지는 점

강의는 2022년 기준(Boot 2.6, Kotlin 1.6)이다. 최신 스택에서는 다음을 확인하자.

- **Spring Boot 3.x**: `javax.persistence.*` 대신 **`jakarta.persistence.*`**를 쓰고, JDK 17 이상이 필요하다.
- **Gradle Kotlin DSL(`build.gradle.kts`)**이 표준이다. 플러그인은 `kotlin("jvm")`, `kotlin("plugin.spring")`, `kotlin("plugin.jpa")`로 선언한다.
- **Entity도 open 처리하는 것이 권장된다.** `plugin.spring`은 `@Entity`를 open하지 않으므로, Entity가 `final`이면 `@ManyToOne(fetch = LAZY)` 프록시가 동작하지 않을 수 있다.
  ```kotlin
  allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
  }
  ```
- Querydsl을 Boot 3와 함께 쓸 때는 `querydsl-jpa:5.x:jakarta` / `querydsl-apt:5.x:jakarta` classifier를 사용한다. kapt는 유지보수 모드이므로 KSP 기반 대안도 검토할 만하다.

> 위 버전 관련 내용은 강의 자료에 없는 보충 설명이다. 적용 전에 공식 문서로 최신 버전을 확인하자.
