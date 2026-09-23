# Kotlin + Spring Boot + JPA + Querydsl + MySQL 실전 가이드

**Java 서버 개발 경력자가 Kotlin 회사에 합류했을 때 필요한 것들을 한 곳에 모은 문서.** 모든 설명은 "Java에서는 이렇게 했는데, Kotlin에서는 이렇게"로 비교하고, 코드는 전부 `library-api/`에서 실제로 컴파일·테스트했다.

> 예제 프로젝트: [`library-api/`](../library-api) — 작가(Author) 1:N 책(Book) CRUD API
> 검증 환경(2026-09-22): Spring Boot 4.1.1 · Kotlin 2.3.21 · Hibernate 7.4 · Querydsl(OpenFeign) 7.6 · Gradle 9.7.1 · JDK 25
> 테스트 79개 전부 통과 (MySQL · Redis는 Testcontainers로 실제 컨테이너, Kafka는 EmbeddedKafka) — `./gradlew test`
> 쿠버네티스 매니페스트 10개는 `helm lint` · `helm template` · `kubeconform`(스키마 검증) 통과
> 강의 요약본은 [`java-to-kotlin-spring.md`](java-to-kotlin-spring.md)에 있다.

## 목차
0. [읽는 법 · 먼저 알아둘 용어](#0-읽는-법--먼저-알아둘-용어)
1. [강의 정리만으로 충분한가?](#1-강의-정리만으로-충분한가)
2. [Kotlin 기초: val/var · mutable/immutable · nullable · 주 생성자 · backing property · companion object · @JvmStatic · data class · 생성자 주입](#2-kotlin-기초)
3. [프로젝트 설정 (Gradle Kotlin DSL)](#3-프로젝트-설정)
4. [MySQL 설정과 주의사항](#4-mysql)
5. [Entity 설계 (Kotlin + JPA)](#5-entity-설계)
6. [연관관계: @ManyToOne · @OneToMany](#6-연관관계)
7. [조회 성능: LAZY · N+1 · fetch join · 페이징](#7-조회-성능)
8. [Projection: 필요한 컬럼만 조회하기](#8-projection)
9. [Querydsl](#9-querydsl)
10. [CRUD API 만들기](#10-crud-api)
11. [테스트: JUnit5 · Mock · 효율적인 테스트 전략](#11-테스트)
12. [최종 체크리스트](#12-체크리스트)
13. [Java 개발자를 위한 Kotlin 관용구](#13-java-개발자를-위한-kotlin-관용구)
14. [Java ↔ Kotlin 상호운용](#14-java--kotlin-상호운용)
15. [코루틴](#15-코루틴)
16. [팀 도구 · 컨벤션](#16-팀-도구--컨벤션)
17. [Java 개발자가 자주 하는 실수 Top 10](#17-java-개발자가-자주-하는-실수-top-10)
18. [첫 2주 학습 로드맵](#18-첫-2주-학습-로드맵)
19. [Kafka](#19-kafka)
20. [Redis](#20-redis)
21. [컨테이너와 쿠버네티스](#21-컨테이너와-쿠버네티스)
22. [Helm 차트](#22-helm-차트)
23. [ArgoCD (GitOps)](#23-argocd-gitops)
24. [Prometheus · Grafana](#24-prometheus--grafana)
25. [nginx (Ingress)](#25-nginx-ingress)

---

## 0. 읽는 법 · 먼저 알아둘 용어

**읽는 순서 추천**
- Kotlin이 처음이라면: **2장(기초) → 13장(관용구) → 17장(흔한 실수)** 을 먼저 읽고, 나머지는 필요할 때 찾아본다.
- 바로 코드를 읽어야 한다면: **10장(CRUD API)** 의 코드부터 보고, 모르는 문법이 나오면 2장과 13장으로 돌아간다.
- 각 장의 예제는 `library-api/src/test/kotlin/.../learning/` 에 **실행 가능한 테스트**로 있다. IntelliJ에서 테스트 옆 ▶ 버튼을 눌러 직접 값을 바꿔 보면 가장 빨리 익힌다.

**먼저 알아둘 용어** (Java 경력자라면 대부분 아는 것이지만, Kotlin에서 새로 등장하는 것 위주로)

| 용어 | 쉽게 말하면 |
|---|---|
| 프로퍼티(property) | Java의 "필드 + getter(+ setter)" 묶음. `val name`이라고 쓰면 필드와 getter가 한 번에 생긴다 |
| 주 생성자 | 클래스 이름 바로 뒤 괄호. `class Book(val title: String)` |
| nullable 타입 | 이름 뒤에 `?`가 붙은 타입(`String?`). null을 담을 수 있다는 표시. 없으면 null 불가 |
| 확장 함수 | 기존 클래스에 메소드를 "붙인 것처럼" 부르는 함수. 실체는 Java의 static 유틸 메소드 |
| 스코프 함수 | `let`, `apply`, `also`, `run`, `with`. 객체 하나를 두고 블록 안에서 무언가를 할 때 쓰는 함수 |
| companion object | 클래스에 딱 하나 있는 동반 객체. Java의 static 멤버 자리 |
| 코루틴 | 스레드를 붙잡지 않고 "잠시 멈췄다가 이어서" 실행할 수 있는 가벼운 작업 단위 |
| suspend 함수 | 잠시 멈출 수 있는 함수. 코루틴 안에서만 부를 수 있다 |
| 영속성 컨텍스트 | JPA가 엔티티를 보관·추적하는 1차 캐시. 여기 들어 있는 엔티티를 바꾸면 커밋 때 UPDATE가 나간다(변경 감지) |
| 프록시 | JPA가 LAZY 로딩을 위해 진짜 엔티티 대신 넣어 두는 "대리 객체". 처음 접근할 때 DB에서 채운다 |
| 스프링 컨텍스트 | 스프링이 Bean들을 만들어 담아 두는 컨테이너. 테스트에서 이걸 띄우는 비용이 크다 |

---

## 1. 강의 정리만으로 충분한가?

**기초로는 충분하지만, 실무용으로는 부족하다.** 강의가 다루지 않거나 지금과 달라진 부분은 다음과 같다.

| 강의 | 실무에서 추가로 필요한 것 |
|---|---|
| H2 인메모리 DB | **MySQL**: 문자셋, 타임존, ID 전략, 스키마 관리(ddl-auto는 로컬에서만) |
| 기능 몇 개(등록/대출/반납) | **CRUD 전체 + 검증(@Valid) + 예외→HTTP 응답 + 페이징** |
| `@ManyToOne`을 기본값(EAGER)으로 사용 | **LAZY + allOpen**, 양방향 편의 메소드, cascade/orphanRemoval의 의미 |
| Projection은 생성자 방식 한 가지 | 인터페이스 / JPQL 생성자 / Querydsl 비교 |
| 모든 테스트를 `@SpringBootTest`로 작성 | 단위 → 슬라이스 → 통합 **테스트 피라미드**, 컨텍스트 캐싱 |
| Spring Boot 2.6, `javax`, `com.querydsl` 5.0 | Boot 4, `jakarta`, Jackson 3, **OpenFeign Querydsl 7** |

---

## 2. Kotlin 기초

### 2-1. `val` vs `var`

| | `val` | `var` |
|---|---|---|
| 재대입 | ❌ 불가 (Java의 `final`) | ✅ 가능 |
| 자동 생성 | getter만 | getter + setter |
| 기본 선택 | **기본값으로 쓴다** | 값이 바뀌어야 할 때만 쓴다 |

```kotlin
val list = mutableListOf(1)
list.add(2)            // ✅ 객체 내부는 바뀐다
// list = mutableListOf()  ❌ 재대입만 막는다
```
- **`val`은 "참조가 고정"일 뿐, "불변 객체"가 아니다.** 진짜 불변이 필요하면 `List`(읽기 전용 인터페이스)나 불변 타입을 쓴다.
- 이 프로젝트에서는 이렇게 쓴다.
  - Entity의 id는 `val`, 바뀌는 값은 `var … protected set`
  - DTO는 전부 `val`
  - 주입받는 의존성은 `private val`

### 2-1b. mutable / immutable — 바꿀 수 있는가, 없는가

**쉽게 말하면**: mutable은 "만든 뒤에도 내용을 바꿀 수 있는" 것, immutable은 "만든 뒤에는 절대 안 바뀌는" 것이다. Kotlin은 **컬렉션 타입 이름에서부터 둘을 구분**한다.

| | 읽기 전용 (read-only) | 변경 가능 (mutable) |
|---|---|---|
| 타입 | `List`, `Set`, `Map` | `MutableList`, `MutableSet`, `MutableMap` |
| 만들기 | `listOf()`, `setOf()`, `mapOf()` | `mutableListOf()`, `mutableSetOf()`, `mutableMapOf()` |
| `add`/`remove` | ❌ 메소드 자체가 없다 (컴파일 에러) | ✅ |
| Java로 치면 | `List.of()`(Java 9+)처럼 쓰는 타입 | `new ArrayList<>()` |

```kotlin
val readOnly: List<String> = listOf("A", "B")
// readOnly.add("C")   ❌ 컴파일 에러
val mutable = mutableListOf("A", "B")
mutable.add("C")       // ✅
```

**꼭 알아야 할 함정: "읽기 전용"은 "불변"이 아니다**
```kotlin
val origin = mutableListOf("A")
val view: List<String> = origin   // 같은 객체를 읽기 전용 "타입"으로 볼 뿐
origin.add("B")
view                               // [A, B] ← 같이 바뀌었다!
val snapshot = origin.toList()     // 복사본을 만들어야 분리된다
```
- `List`는 "**내가** 이 참조로는 못 바꾼다"는 약속이지, 객체 자체가 불변이라는 보장이 아니다.
- 밖으로 내보낼 때 원본과 분리하고 싶으면 `toList()`로 복사한다(backing property에서 `get() = _books.toList()`를 쓴 이유).

**`val` × mutable 조합 정리**

| 선언 | 변수 재대입 | 내용 변경 | 언제 |
|---|---|---|---|
| `val x: List<T>` | ❌ | ❌ | **기본값. 가장 안전** |
| `val x: MutableList<T>` | ❌ | ✅ | 클래스 내부에서 모아 담을 때 (Entity의 `books`) |
| `var x: List<T>` | ✅ | ❌ | 통째로 바꿔 끼우는 상태 (`x = x + item`) |
| `var x: MutableList<T>` | ✅ | ✅ | **피한다** — 바뀌는 경로가 두 개라 추적이 어렵다 |

**불변 객체 만들기**: 모든 프로퍼티를 `val`로 두고, 바꾸고 싶으면 새 객체를 만든다. `data class`의 `copy()`가 이를 돕는다.
```kotlin
data class Price(val amount: Int)
val after = Price(1_000).copy(amount = 2_000)   // 원본은 그대로, 새 객체
val added = listOf(1, 2) + 3                    // 읽기 전용 List도 + 로 "새 리스트"
```
- **왜 불변을 선호하나?** 여러 곳(특히 여러 스레드·코루틴)에서 같은 객체를 봐도 누가 바꿀 걱정이 없다. DTO를 전부 `val`로 만드는 이유다.
- **Entity는 예외**: JPA는 엔티티를 "바꿔서" UPDATE 하는 모델이라 필요한 곳만 `var … protected set`으로 둔다.

예제: `KotlinBasicsTest`의 `List는 읽기 전용…`, `읽기 전용 List로 넘겨도…`, `불변 객체는…`

### 2-2. nullable 처리

Kotlin은 **타입에 null 가능 여부가 들어 있다.** `String`에는 null을 넣을 수 없고, `String?`에만 넣을 수 있다. 컴파일러가 null 처리를 강제한다.

| 문법 | 의미 | 예 |
|---|---|---|
| `T?` | null 가능 타입 | `fun findByName(name: String): Book?` |
| `?.` | null이면 호출하지 않고 null | `author?.name` |
| `?:` (엘비스) | null이면 오른쪽 값 | `name ?: "이름 없음"` |
| `?: throw` / `?: return` | null이면 예외 또는 조기 반환 | `repo.findByName(n) ?: throw NotFoundException()` |
| `?.let { }` | null이 아닐 때만 블록 실행 | `c.title?.let { book.title.contains(it) }` |
| 스마트 캐스트 | `if (x != null)` 안에서는 non-null로 취급 | |
| `!!` | "절대 null 아님" 단언. null이면 NPE | `book.id!!` (저장된 엔티티에만) |
| `requireNotNull(x) { }` | null이면 IllegalArgumentException | |

- **`!!`는 최소한으로 쓴다.** "여기서는 논리적으로 null일 수 없다"를 설명할 수 있을 때만 쓴다(예: 저장된 Entity의 id).
- **Java 코드에서 온 값은 "플랫폼 타입"(`String!`)이다.** Kotlin이 null 여부를 모르므로, 받는 쪽에서 `String?`으로 받거나 Java에 `@Nullable`/`@NonNull`을 붙인다. `-Xjsr305=strict`는 스프링 API의 어노테이션을 엄격하게 반영한다.
- **스프링과 JPA에서는 이렇게 쓴다.**
  - Repository 반환 타입: `Optional<T>` 대신 `T?`
  - 요청 DTO: 필수는 `T`, 선택은 `T? = null`
  - Entity: DB 컬럼 nullable과 Kotlin 타입을 일치시킨다

예제: `library-api/src/test/kotlin/.../learning/KotlinBasicsTest.kt`

### 2-3. 주 생성자 (primary constructor)

Kotlin 클래스는 **클래스 이름 바로 뒤의 괄호가 생성자**다. 이것을 주 생성자라고 한다.

```kotlin
class Member(
	val name: String,            // ① val/var 있음 → 프로퍼티(필드 + getter [+ setter])
	age: Int,                    // ② 아무것도 없음 → 생성자 인자일 뿐 (member.age 불가)
	val role: String = "USER",   // ③ 기본값 → Java의 생성자 오버로딩 대체
) {
	val isAdult: Boolean = age >= 19       // 인자는 프로퍼티 초기화와 init에서만 쓸 수 있다

	init {                                 // 생성 시점에 실행 (여러 개면 위에서부터 순서대로)
		require(name.isNotBlank())
	}

	constructor(name: String) : this(name, age = 0)   // 부생성자: 반드시 this(...)로 주 생성자를 호출
}

Member(name = "김코틀", age = 20)   // 이름 붙인 인자, role은 기본값
```
```java
// ①~③과 같은 일을 하는 Java
public class Member {
	private final String name;  private final String role;  private final boolean isAdult;
	public Member(String name, int age, String role) {
		this.name = name; this.role = role; this.isAdult = age >= 19;
		if (name.isBlank()) throw new IllegalArgumentException();
	}
	public Member(String name, int age) { this(name, age, "USER"); }   // 기본값 대신 오버로딩
	public String getName() { return name; }  /* getRole, isAdult ... */
}
```

| 요소 | 역할 | 이 프로젝트에서 |
|---|---|---|
| `val`/`var` 붙은 파라미터 | 선언과 대입을 한 번에 | DTO 전부, Service의 `private val repository` |
| 표시 없는 파라미터 | 값만 받고 프로퍼티는 body에서 따로 선언 | **Entity**: `class Book(title: String)` + body의 `var title = title; protected set` |
| 기본값 인자 | 오버로딩 대체, 선택 값 | `id: Long? = null`, `status = LOANED`, 검색 조건 `T? = null` |
| 이름 붙인 인자 | 순서와 무관하게 명확히 전달 | `BookResponse(id = …, title = …)` |
| `init` | 생성 시 검증 | Entity의 `require(title.isNotBlank())` |
| 부생성자 `constructor(...)` | 다른 생성 방법 추가 | 거의 안 쓴다 → **정적 팩토리(companion `of()`)를 선호** |

**`constructor` 키워드를 꼭 써야 하는 경우**: 주 생성자에 **어노테이션이나 접근 제한자**를 붙일 때다.
```kotlin
class BookServiceTest @Autowired constructor(…)   // 어노테이션
class Money private constructor(val amount: Long)  // private 생성자 → 정적 팩토리로만 생성
```

**JPA와의 관계**: JPA는 인자 없는 기본 생성자로 객체를 만든 뒤 필드에 값을 채운다. Kotlin 주 생성자에는 인자가 있으므로 `plugin.jpa`가 **숨은 기본 생성자**를 추가해 준다. 이 생성자는 `init` 블록과 프로퍼티 초기화를 실행하지 않으므로, DB에서 읽어 온 값은 `init` 검증을 거치지 않는다.

### 2-4. backing property — 안에서는 변경, 밖에는 읽기 전용

**backing property**는 실제 데이터를 담는 `private` 프로퍼티(관례상 `_` 접두사)를 두고, 외부에는 **읽기 전용 프로퍼티를 따로 공개**하는 패턴이다.

```kotlin
class Shelf {
	private val _books = mutableListOf<String>()   // 실제 데이터 (변경 가능)
	val books: List<String>                         // 공개용 (읽기 전용 타입)
		get() = _books

	private var _count = 0
	val count: Int
		get() = _count

	fun add(title: String) {                        // 변경은 이 메소드로만
		_books.add(title)
		_count++
	}
}

shelf.books.add("x")   // ❌ 컴파일 에러: List에는 add가 없다
shelf.count = 5        // ❌ 컴파일 에러: val에는 setter가 없다
```

**setter를 숨기는 3가지 방법 비교**

| 방법 | 코드 | 장점 | 단점 |
|---|---|---|---|
| backing property | `private var _name` + `val name get() = _name` | 가장 확실하다. **컬렉션을 읽기 전용 타입으로 노출**할 수 있다 | 프로퍼티마다 두 줄씩 늘어난다 |
| `private set` | `var name = name; private set` | 간결하다 | **allOpen(Entity)에서는 컴파일 에러** — open 프로퍼티에는 private setter를 둘 수 없다 |
| `protected set` | `var name = name; protected set` | 간결하고 Entity에서도 된다 | 하위 클래스에서는 바꿀 수 있다 (실무상 문제는 거의 없다) |

**JPA Entity에 적용할 때**
- **단일 값**(`name`, `title`)에는 `protected set`이 가장 간단하다. 이 프로젝트의 선택이다.
- **컬렉션**(`@OneToMany`)은 `protected set`으로도 **리스트 내부의 add/remove를 막을 수 없다.** 완전히 막으려면 backing property를 쓴다.
  ```kotlin
  @OneToMany(mappedBy = "author", cascade = [CascadeType.ALL], orphanRemoval = true)
  private val _books: MutableList<Book> = mutableListOf()   // JPA는 이 필드를 매핑

  val books: List<Book>                                     // 외부 공개: 읽기 전용
  	get() = _books.toList()                              // 복사본 → 밖에서 캐스팅해도 원본 변경 불가
  ```
- **트레이드오프**
  - JPA와 Querydsl은 **필드 이름**을 쓴다. 그래서 JPQL은 `a._books`, Querydsl은 `author._books`처럼 밑줄이 드러난다(`mappedBy`는 Book 쪽 필드명이라 영향이 없다).
  - `toList()` 복사는 호출할 때마다 새 리스트를 만든다. 성능이 신경 쓰이면 `get() = _books`로 두고 읽기 전용 타입만으로 막는다.
  - 이 예제 프로젝트는 쿼리 가독성을 위해 `val books: MutableList<Book>`을 공개하고 **"편의 메소드로만 변경"을 컨벤션으로** 정했다(§6-4). 팀이 더 엄격한 캡슐화를 원하면 backing property로 바꾼다.

예제: `library-api/src/test/kotlin/.../learning/KotlinBasicsTest.kt`의 `Shelf`

### 2-5. `companion object` — Java의 `static` 자리

Kotlin에는 `static` 키워드가 없다. 그 대신 **클래스마다 하나만 존재하는 객체**를 `companion object`로 선언한다. 이 안에 둔 멤버는 클래스 이름으로 호출한다.

```kotlin
class Money private constructor(val amount: Long) {
	companion object {
		const val MAX = 1_000_000L            // const val → Java의 static final 상수
		fun of(amount: Long): Money {         // 정적 팩토리
			require(amount in 0..MAX)
			return Money(amount)
		}
	}
}
Money.of(1_000)   // 클래스 이름으로 호출
```

이 프로젝트에서는 다음 용도로 쓴다.
- **DTO 정적 팩토리**: `BookResponse.of(book)`. Entity를 DTO로 바꾸는 책임을 DTO가 맡는다.
- **Test Fixture**: `Book.fixture(...)`. 기본값이 있는 인자로 테스트 데이터를 쉽게 만든다.
- **Testcontainers 컨테이너**: 클래스당 하나만 띄워야 하므로 companion object에 둔다.

> 컨벤션: `companion object`는 **클래스의 맨 아래**에 둔다.

### 2-6. `@JvmStatic` — Java(=JUnit·프레임워크)가 static을 요구할 때

- `companion object`의 함수는 바이트코드상으로 `Companion`이라는 **객체의 인스턴스 메소드**다. Java에서는 `Money.Companion.of()`처럼 호출해야 하고, 진짜 static 메소드가 아니다.
- **JUnit5의 `@BeforeAll`/`@AfterAll`은 static 메소드를 요구한다.** 그래서 `@JvmStatic`을 붙여 진짜 static 메소드도 함께 생성해야 한다.

```kotlin
class JUnitLifecycleTest {
	companion object {
		@JvmStatic
		@BeforeAll
		fun beforeAll() { /* 모든 테스트 전에 1번 */ }
	}
}
```

`@JvmStatic` 없이 쓰는 대안은 두 가지다.

```kotlin
@TestInstance(TestInstance.Lifecycle.PER_CLASS)  // 테스트 인스턴스를 클래스당 1개로 → static이 필요 없다
class SomeTest {
	@BeforeAll
	fun setUp() { }
}
```

| 상황 | 필요한 것 |
|---|---|
| JUnit `@BeforeAll`/`@AfterAll` | `companion object` + `@JvmStatic`, 또는 `@TestInstance(PER_CLASS)` |
| Testcontainers `@Container` 필드를 클래스당 하나로 공유 | `companion object` + `@JvmStatic` |
| Java 코드에서 `Money.of()`로 호출하고 싶을 때 | `@JvmStatic` |
| Kotlin 코드에서만 쓸 때 | 필요 없음 |

예제: `library-api/src/test/kotlin/.../learning/JUnitLifecycleTest.kt`

### 2-7. `data class` — 값 객체용 클래스

`data class`로 선언하면 **주 생성자의 프로퍼티**를 기준으로 다음이 자동 생성된다.

| 자동 생성 | 의미 |
|---|---|
| `equals()` / `hashCode()` | 값이 같으면 같은 객체로 본다 (`Point(1,2) == Point(1,2)`) |
| `toString()` | `Point(x=1, y=2)` |
| `copy()` | 일부 값만 바꾼 새 객체 (`p.copy(y = 5)`) |
| `componentN()` | 구조 분해 (`val (x, y) = p`) |

- 클래스 **body에 선언한 프로퍼티는 비교 대상이 아니다.**
- 예제: `library-api/src/test/kotlin/.../learning/KotlinBasicsTest.kt`

### 2-8. `class XTest @Autowired constructor(private val x: X)` ↔ Java

Kotlin 한 줄이 Java의 **필드 선언 + 생성자 + 대입**을 한 번에 한다.

```kotlin
@SpringBootTest
class BookServiceTest @Autowired constructor(
	private val bookService: BookService,
)
```
```java
// 위 Kotlin 코드와 동일한 Java
@SpringBootTest
class BookServiceTest {
	private final BookService bookService;          // private val → private final 필드 + (private이라 getter 없음)

	@Autowired                                      // @Autowired constructor → 생성자에 붙은 @Autowired
	public BookServiceTest(BookService bookService) {
		this.bookService = bookService;
	}
}
```
컴파일된 바이트코드를 `javap`로 확인하면 실제로 `private final` 필드 + `@Autowired`가 붙은 생성자다.
```
private final com.example.library.book.BookService bookService;
public com.example.library.book.BookServiceTest(com.example.library.book.BookService, …);
  RuntimeVisibleAnnotations: org.springframework.beans.factory.annotation.Autowired
```

**왜 테스트에만 `@Autowired`가 필요한가?**
- `@Service` 같은 **스프링 Bean**은 생성자가 하나면 스프링이 알아서 주입한다(`@Autowired` 생략 가능).
- **테스트 클래스는 스프링 Bean이 아니라 JUnit이 만든다.** JUnit의 SpringExtension이 생성자 파라미터를 채우려면 `@Autowired`로 표시해야 한다.
- 모든 테스트에서 생략하고 싶다면 `src/test/resources/junit-platform.properties`에 `spring.test.constructor.autowire.mode=all`을 넣는다. 클래스 단위로는 `@TestConstructor(autowireMode = ALL)`을 쓴다.

### 2-9. JPA와 함께 써도 되나? (핵심 표)

| 문법 | Entity | DTO (요청/응답) | 이유 |
|---|---|---|---|
| `data class` | ❌ **쓰지 않는다** | ✅ 권장 | 아래 설명 참고 |
| `class` + `allOpen` | ✅ | ○ | Entity는 open이어야 LAZY 프록시가 생성된다 |
| `companion object` | ✅ (fixture, 팩토리) | ✅ (`of()`) | JPA 매핑과 무관하다. 필드로 매핑되지 않는다 |
| `val` 프로퍼티 | ✅ id·불변 값 | ✅ | Hibernate는 필드에 리플렉션으로 값을 넣으므로 `val`이어도 된다 |
| `var ... protected set` | ✅ 변경 가능한 값 | – | allOpen 때문에 `private set`은 컴파일 에러가 난다 |
| `lateinit var` | △ 피한다 | – | 기본 타입에 쓸 수 없고, 초기화 전에 접근하면 예외가 난다 |
| `object` (싱글톤) | ❌ | – | Entity가 될 수 없다 |

**Entity에 `data class`를 쓰면 안 되는 이유**
1. **`equals`/`hashCode`/`toString`이 연관관계를 따라간다.** `Author.books` → `Book.author` → `Author.books` …로 순환해서 StackOverflowError가 나거나, LAZY 컬렉션이 의도치 않게 로딩된다.
2. **`hashCode`가 변하는 값(id 포함)에 의존한다.** 저장 전(id=null)과 후의 hashCode가 달라서, `HashSet`에 넣은 Entity를 다시 찾지 못한다.
3. **`copy()`는 JPA가 관리하지 않는 새 인스턴스를 만든다.** 변경 감지가 동작하지 않는다.
4. **data class는 final이다.** allOpen으로 열 수는 있지만 의미가 충돌한다.

**Entity의 equals/hashCode는 어떻게 하나?**
- 대부분은 **재정의하지 않는다**(참조 비교). 같은 영속성 컨텍스트 안에서는 같은 행이면 같은 인스턴스가 보장된다.
- `Set`에 담거나 여러 영속성 컨텍스트에 걸쳐 비교해야 할 때만 id 기반으로 구현한다. 이때 id가 null이면 다른 객체로 보고, hashCode는 클래스 기준 상수로 두며, Hibernate 프록시를 고려해 `Hibernate.getClass()`로 타입을 비교한다.

---

## 3. 프로젝트 설정

전체 파일: `library-api/build.gradle.kts`

```kotlin
plugins {
	kotlin("jvm") version "2.3.21"
	kotlin("plugin.spring") version "2.3.21" // @Component/@Transactional 클래스 open
	kotlin("plugin.jpa") version "2.3.21"    // @Entity no-arg 생성자
	kotlin("kapt") version "2.3.21"          // Querydsl Q클래스
	id("org.springframework.boot") version "4.1.1"
	id("io.spring.dependency-management") version "1.1.7"
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("tools.jackson.module:jackson-module-kotlin")        // Jackson 3 (패키지 tools.jackson)
	implementation("io.github.openfeign.querydsl:querydsl-jpa:7.6")
	kapt("io.github.openfeign.querydsl:querydsl-apt:7.6:jpa")
	runtimeOnly("com.mysql:mysql-connector-j")
	developmentOnly("org.springframework.boot:spring-boot-docker-compose")
	// test: data-jpa-test, webmvc-test, testcontainers-mysql, h2 ...
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}
```

| 항목 | 왜 필요한가 |
|---|---|
| `plugin.spring` | Kotlin 클래스는 기본이 final이다. 스프링 AOP 프록시(@Transactional 등)는 상속이 필요하다 |
| `plugin.jpa` | JPA는 인자 없는 생성자가 필요하다 |
| **`allOpen { Entity }`** | plugin.spring은 **@Entity를 열지 않는다**. final Entity는 LAZY 프록시를 만들 수 없어서 사실상 즉시 로딩이 된다. Spring Initializr도 기본으로 넣어준다 |
| `-Xjsr305=strict` | 스프링 API의 `@Nullable`을 Kotlin의 `T?`로 엄격하게 해석한다 |
| `-Xannotation-default-target=param-property` | 생성자 프로퍼티에 붙인 어노테이션을 파라미터와 필드에 함께 적용한다 (§10-3) |
| `jackson-module-kotlin` | 기본 생성자·setter가 없는 Kotlin DTO를 JSON에서 생성한다 |
| `kotlin-reflect` | 스프링이 Kotlin 클래스(기본값 인자 등)를 리플렉션으로 다룰 때 필요하다 |

**Querydsl 라이브러리 선택**
- 원조 `com.querydsl` 5.x는 사실상 업데이트가 멈췄다. **OpenFeign 포크 `io.github.openfeign.querydsl`**이 Hibernate 7과 jakarta를 지원한다. 사용하는 API(`JPAQueryFactory`, `QBook` 등)는 같다.
- Q클래스는 `kapt`로 생성된다(`build/generated/source/kapt`). kapt는 유지보수 모드이므로, 대안으로 KSP 기반 `querydsl-ksp-codegen`도 있다(이 프로젝트에서는 검증하지 않았다).

---

## 4. MySQL

### 4-1. 로컬 실행
```bash
# Docker Desktop을 켠 상태에서
cd library-api && ./gradlew bootRun
# → spring-boot-docker-compose가 compose.yaml의 MySQL 8.4를 띄우고 접속 정보를 자동 주입
```
- `compose.yaml`: `utf8mb4`, `utf8mb4_0900_ai_ci`, `TZ=UTC`
- 테스트는 기본적으로 H2(MySQL 모드)로 돈다. 실제 MySQL로 검증하는 테스트는 `MySqlContainerTest`(Testcontainers)이며, Docker가 켜져 있을 때만 실행된다.

### 4-2. `application.yml` 핵심
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/library?serverTimezone=UTC&characterEncoding=UTF-8
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: update          # 로컬만! 운영은 validate/none + Flyway
    properties:
      hibernate:
        default_batch_fetch_size: 100
        jdbc.time_zone: UTC
```

### 4-3. 주의사항

| 주제 | 주의할 점 |
|---|---|
| **ID 전략** | MySQL은 `GenerationType.IDENTITY`(AUTO_INCREMENT)를 쓴다. `save()` 시점에 즉시 INSERT가 나가서 id가 채워진다. 대신 **JDBC 배치 INSERT가 비활성화**되므로, 대량 삽입은 JdbcTemplate `batchUpdate` 등으로 따로 처리한다 |
| **스키마 관리** | `ddl-auto: create/update`는 로컬과 테스트에서만 쓴다. 운영은 **Flyway/Liquibase** 마이그레이션 + `validate`를 쓴다 |
| **문자셋** | `utf8`(=utf8mb3)은 이모지가 깨진다. **`utf8mb4`**를 쓴다 |
| **타임존** | 서버·JDBC·Hibernate를 **UTC로 통일**한다(`serverTimezone=UTC`, `hibernate.jdbc.time_zone`). 화면에서 KST로 변환한다 |
| **Enum** | `@Enumerated(EnumType.STRING)` + `@Column(length = …)`. 기본값 ORDINAL은 순서가 바뀌면 데이터가 깨진다 |
| **NOT NULL** | Kotlin의 `String`(non-null)만으로는 DDL에 NOT NULL이 붙지 않는다. **`@Column(nullable = false)`를 명시**한다 |
| **예약어** | `user`, `order`, `group` 같은 테이블명은 피한다(`users`, `orders`). `@Table(name = …)`으로 명시한다 |
| **인덱스** | 검색 조건 컬럼과 FK에 인덱스를 둔다(`@Table(indexes = [...])`, 운영은 마이그레이션 스크립트). MySQL InnoDB는 FK에 인덱스를 자동 생성한다 |
| **LIKE 검색** | `contains`는 `LIKE '%x%'`가 되어 인덱스를 못 탄다. 앞부분 일치(`startsWith`)나 전문 검색을 고려한다 |
| **open-in-view** | 기본값(true)이면 View까지 DB 커넥션을 붙잡는다. **false**로 두고, DTO 변환을 트랜잭션 안에서 끝낸다 |
| **커넥션 풀** | HikariCP `maximum-pool-size`는 DB `max_connections`와 인스턴스 수를 고려해 정한다 |

---

## 5. Entity 설계

`library-api/src/main/kotlin/com/example/library/book/Book.kt`

```kotlin
@Entity
@Table(name = "book", indexes = [Index(name = "idx_book_title", columnList = "title")])
class Book(
	title: String,          // val/var 없음 = 생성자 인자일 뿐, 프로퍼티가 아니다
	type: BookType,
	price: Int,
	author: Author,
) : BaseTimeEntity() {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	val id: Long? = null    // 저장 전에는 null

	@Column(nullable = false, length = 200)
	var title: String = title
		protected set       // 외부에서 book.title = "x" 불가. 변경은 update()로만

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	var type: BookType = type
		protected set

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "author_id", nullable = false)
	var author: Author = author
		protected set

	init { validate(title, price) }

	fun update(title: String, type: BookType, price: Int) { … }
}
```

**패턴 설명**
- **생성자 인자와 body 프로퍼티를 분리한다.** 생성자는 필요한 값만 받고, 프로퍼티는 body에서 `protected set`으로 캡슐화한다(강의의 "setter를 막는 방법"을 적용한 것).
- **`private set`이 아니라 `protected set`인 이유**: allOpen 플러그인이 프로퍼티까지 open하는데, Kotlin은 **open 프로퍼티에 private setter를 허용하지 않는다.**
- **검증은 `init`과 도메인 메소드에서 한다.** `require()`는 IllegalArgumentException을 던진다.
- **공통 컬럼**: `BaseTimeEntity`(`@MappedSuperclass` + `@CreatedDate`/`@LastModifiedDate`)를 상속하고, `@EnableJpaAuditing`을 켠다.
  - `@EnableJpaAuditing`은 **main 클래스가 아니라 별도 `@Configuration`(JpaConfig)**에 둔다. main에 두면 `@WebMvcTest`가 JPA 없이 뜰 때 에러가 난다.
- **id 타입**: `Long? = null`이 가장 무난하다. DTO로 바꿀 때는 `book.id!!`(저장된 엔티티라는 단언)을 쓴다.
- **생성자에 `id: Long? = null`을 둔 이유**: 운영 코드에서는 생략해서 DB가 채우게 하고, **Mock 테스트에서만 id가 있는 객체를 만들 때** 지정한다(`Book(…, id = 10L)`). id가 있는 객체를 `save()`에 넘기면 Spring Data가 새 엔티티가 아니라고 보고 `merge`하므로, 운영 코드에서는 넘기지 않는다.

---

## 6. 연관관계

### 6-1. 전체 그림
```
author (1) ──────< book (N)
  id                 id
  name               author_id  (FK) ← 연관관계의 주인
```
- **FK를 가진 쪽(N, Book)이 연관관계의 주인**이다. DB에 반영되는 것은 주인 쪽 값(`book.author`)뿐이다.
- 1 쪽(Author.books)은 **`mappedBy`로 선언하는 읽기용 거울**이다. 여기에 add해도 FK는 바뀌지 않는다.

### 6-2. `@ManyToOne` (N 쪽, 주인)
```kotlin
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "author_id", nullable = false)
var author: Author = author
	protected set
```
| 체크 | 이유 |
|---|---|
| **`fetch = LAZY` 필수** | `@ManyToOne`/`@OneToOne`의 기본값은 **EAGER**다. 목록을 조회하면 연관 엔티티까지 줄줄이 조회되고 N+1이 생긴다 |
| **allOpen** | LAZY 프록시는 Entity를 상속해서 만든다. final이면 프록시를 못 만든다 |
| `optional = false` + `nullable = false` | 필수 관계라면 명시한다. inner join 최적화와 NOT NULL DDL로 이어진다 |
| `@JoinColumn(name)` | FK 컬럼명을 명시한다 |

LAZY가 실제로 동작하는지는 `AuthorRelationTest.manyToOneLazyProxy`에서 확인한다(`Hibernate.isInitialized(book.author) == false`).

### 6-3. `@OneToMany` (1 쪽, 거울)
```kotlin
@OneToMany(mappedBy = "author", cascade = [CascadeType.ALL], orphanRemoval = true)
val books: MutableList<Book> = mutableListOf()
```
| 옵션 | 의미 | 언제 쓰나 |
|---|---|---|
| `mappedBy = "author"` | Book.author 필드가 주인이라는 뜻이다 | 양방향이면 항상 |
| `cascade = [ALL]` | Author를 저장·삭제하면 books도 함께 처리된다 | **부모가 자식의 생명주기를 완전히 소유**할 때만 |
| `orphanRemoval = true` | 컬렉션에서 빠진 Book을 DELETE한다 | 위와 같음 |
| 기본 fetch | LAZY(컬렉션) | 그대로 둔다 |

- **`cascade`와 `orphanRemoval`은 신중하게 쓴다.** 여러 부모가 공유하는 엔티티(예: Book을 Library와 Author가 같이 참조)에 걸면 의도치 않은 삭제가 일어난다.
- **Kotlin 배열 인자**는 `cascade = [CascadeType.ALL]`처럼 `[ ]`로 감싼다.

### 6-4. 양방향 편의 메소드
주인(Book.author)과 거울(Author.books)을 **항상 함께** 맞춘다. 그렇지 않으면 같은 트랜잭션 안에서 메모리와 DB 상태가 어긋난다.
```kotlin
// Author
fun writeBook(title: String, type: BookType, price: Int): Book =
	Book(title, type, price, author = this).also { books.add(it) }

// Book
fun changeAuthor(newAuthor: Author) {
	if (author === newAuthor) return
	author.removeBook(this)
	author = newAuthor
	newAuthor.addBook(this)
}
```
- `addBook`/`removeBook`은 `internal`로 두어 모듈 밖에서는 호출하지 못하게 한다.
- 더 엄격하게 하려면 `private val _books = mutableListOf<Book>()` + `val books: List<Book> get() = _books`로 **읽기 전용만 노출**한다(Kotlin backing property 패턴). 이때 Querydsl의 경로가 `author._books`가 되는 트레이드오프가 있다.

### 6-5. 연관관계 설계 원칙
1. **처음에는 단방향 `@ManyToOne`만 둔다.** 1 쪽에서 컬렉션 탐색이 정말 필요할 때 `@OneToMany(mappedBy)`를 추가한다.
2. **`@OneToMany` 단방향(`@JoinColumn`을 1 쪽에 둔 형태)은 피한다.** 자식 INSERT 후 FK를 채우는 UPDATE가 추가로 나간다.
3. **`@ManyToMany`는 쓰지 않는다.** 중간 테이블에 컬럼(일시, 수량)을 추가할 수 없다. 중간 엔티티를 두고 `@ManyToOne` 두 개로 푼다(강의의 UserLoanHistory가 바로 이 형태다).
4. **Entity를 API 응답으로 그대로 반환하지 않는다.** 양방향 순환 참조로 JSON이 무한 반복되고, LAZY 프록시 직렬화 에러가 나며, 내부 구조가 노출된다. **항상 DTO로 변환한다.**

---

## 7. 조회 성능

### 7-1. N+1이 생기는 이유
`authorRepository.findAll()`(1번)을 실행한 뒤 각 `author.books`에 접근하면 LAZY 컬렉션이 초기화되면서 SELECT가 N번 더 나간다.

### 7-2. 해결 방법 3가지

| 방법 | 코드 | 특징 |
|---|---|---|
| **fetch join** | `join fetch b.author` / Querydsl `.join(book.author, author).fetchJoin()` | 한 번의 SQL로 가져온다. 가장 확실하다 |
| **배치 사이즈** | `hibernate.default_batch_fetch_size: 100` | LAZY 로딩을 `IN (?, ?, …)`으로 묶어서 N+1이 1+1로 줄어든다. **페이징과 함께 쓸 수 있다** |
| **DTO Projection** | §8 | 엔티티를 거치지 않고 필요한 컬럼만 조회한다 |

### 7-3. fetch join의 규칙 (실측 SQL로 확인)
```kotlin
// 1:N 컬렉션 fetch join — 연관관계 "경로"로 조인해야 적용된다
queryFactory.selectFrom(author)
	.leftJoin(author.books, book).fetchJoin()
	.where(author.id.eq(authorId))
	.fetchOne()
```
실제 SQL은 한 번만 나간다(테스트 로그에서 확인).
```sql
select a1_0.id, b1_0.author_id, b1_0.id, …, a1_0.name
from author a1_0 left join book b1_0 on a1_0.id=b1_0.author_id where a1_0.id=?
```
- **`leftJoin(엔티티).on(...)`처럼 경로 없이 조인하면 fetch join이 되지 않는다.** 강의 완성본 코드가 이 형태다.
- **Hibernate 6 이상에서는 `DISTINCT`가 필요 없다.** 컬렉션 fetch join의 중복 엔티티를 자동으로 제거한다(강의는 Hibernate 5 기준이라 DISTINCT를 붙였다).
- **컬렉션(1:N) fetch join + 페이징은 쓰지 않는다.** Hibernate가 전체 데이터를 메모리로 가져와서 페이징한다(경고 로그 `HHH90003004`, 설정에 따라 예외). 대신 아래 방식을 쓴다.
  - N:1 쪽(Book → Author)에서 fetch join + 페이징
  - 또는 부모만 페이징 조회하고, 컬렉션은 `default_batch_fetch_size`로 로딩
- **N:1 fetch join은 row 수가 늘지 않으므로 페이징에 안전하다**(`BookQueryRepository.search`).

---

## 8. Projection

엔티티 전체가 아니라 **필요한 컬럼만 DTO로 조회**하면 전송량과 영속성 컨텍스트 부담이 줄고, 변경 감지 대상에서도 빠진다.

| 방식 | 예제 | 장점 | 단점 |
|---|---|---|---|
| **인터페이스 Projection** (Spring Data) | `fun findAllByAuthorIdOrderByIdAsc(id: Long): List<BookTitleView>` | 인터페이스만 선언하면 된다 | 단순 조건에만 쓸 수 있다. 중첩 시 최적화가 제한된다 |
| **JPQL 생성자 표현식** | `select new com.example…AuthorBookCount(a.id, a.name, count(b)) …` | 집계·조인도 가능하다 | **패키지 전체 경로를 문자열로** 써야 해서 오타를 런타임에야 안다 |
| **Querydsl `Projections.constructor`** | `Projections.constructor(BookSummary::class.java, book.id, …)` | 컴파일 체크, 동적 쿼리와 조합할 수 있다 | 생성자 인자 **순서와 타입**이 맞아야 한다(런타임 검증) |
| **Querydsl `@QueryProjection`** | DTO 생성자에 어노테이션을 붙이면 `QBookSummary` 생성 | 인자 타입까지 컴파일 체크 | DTO가 Querydsl에 의존한다 |

```kotlin
// 인터페이스 Projection — Kotlin val 프로퍼티 = getter로 인식
interface BookTitleView {
	val id: Long
	val title: String
}
```
- **`count()`의 결과는 `Long`이다.** DTO 필드를 `Long`으로 맞춘다(Int면 생성자를 찾지 못한다).
- Projection 결과(DTO)는 **영속 상태가 아니다.** 수정하려면 엔티티를 다시 조회한다.

---

## 9. Querydsl

### 9-1. 설정
```kotlin
@Configuration
@EnableJpaAuditing
class JpaConfig {
	@Bean
	fun jpaQueryFactory(entityManager: EntityManager) = JPAQueryFactory(entityManager)
}
```

### 9-2. Repository 구성 (별도 클래스 방식)
- **단순 CRUD와 메소드 이름 쿼리**는 `BookRepository : JpaRepository`에 둔다.
- **동적 쿼리, 집계, Projection**은 `@Repository class BookQueryRepository(queryFactory)`에 둔다.
- (다른 방식: `BookRepositoryCustom` 인터페이스 + `Impl` 클래스. 장단점은 강의 정리 §8 참고)

### 9-3. 동적 검색 + 페이징
```kotlin
fun search(condition: BookSearchCondition, pageable: Pageable): Page<BookSummary> {
	val content = queryFactory
		.select(Projections.constructor(BookSummary::class.java, book.id, book.title, book.type, book.price, author.name))
		.from(book)
		.join(book.author, author)
		.where(*conditions(condition))
		.orderBy(book.id.desc())
		.offset(pageable.offset)
		.limit(pageable.pageSize.toLong())
		.fetch()

	return PageableExecutionUtils.getPage(content, pageable) {   // 필요할 때만 count 쿼리
		queryFactory.select(book.count()).from(book).join(book.author, author)
			.where(*conditions(condition)).fetchOne() ?: 0L
	}
}

private fun conditions(c: BookSearchCondition): Array<BooleanExpression?> = arrayOf(
	c.title?.takeIf { it.isNotBlank() }?.let { book.title.contains(it) },
	c.type?.let { book.type.eq(it) },
	c.authorName?.takeIf { it.isNotBlank() }?.let { author.name.eq(it) },
	c.minPrice?.let { book.price.goe(it) },
	c.maxPrice?.let { book.price.loe(it) },
)   // where()는 null을 무시하고 나머지를 AND로 묶는다
```
- **Kotlin `?.let`**으로 "값이 있을 때만 조건을 만든다"를 한 줄로 쓴다.
- `where(*array)`는 **spread 연산자**로 가변 인자에 배열을 넘긴다.
- `fetchResults()`와 `fetchCount()`는 deprecated이므로 **count 쿼리를 따로 작성**한다.
- `PageableExecutionUtils.getPage`는 첫 페이지 결과가 size보다 작거나 마지막 페이지이면 count 쿼리를 생략한다.

---

## 10. CRUD API

### 10-1. 엔드포인트

| Method | URL | 설명 | 핵심 기술 |
|---|---|---|---|
| POST | `/api/authors` | 작가 등록 | 중복 검사 → 409 |
| GET | `/api/authors/{id}` | 작가 + 책 목록 | 1:N fetch join |
| GET | `/api/authors/{id}/book-titles` | 제목 목록 | 인터페이스 Projection |
| GET | `/api/authors/stats/book-counts` | 작가별 권수 | JPQL 생성자 Projection + LEFT JOIN |
| PATCH | `/api/authors/{id}` | 이름 변경 | 변경 감지 |
| DELETE | `/api/authors/{id}` | 작가 삭제 | cascade + orphanRemoval |
| POST | `/api/books` | 책 등록 (201) | 편의 메소드 `writeBook` |
| GET | `/api/books/{id}` | 책 조회 | N:1 fetch join |
| GET | `/api/books?title=&type=&authorName=&minPrice=&maxPrice=&page=&size=` | 검색 | Querydsl 동적 쿼리 + 페이징 |
| GET | `/api/books/stats/types` | 분야별 권수 | GROUP BY Projection |
| PATCH | `/api/books/{id}` | 수정(작가 변경 포함) | 변경 감지 + `changeAuthor` |
| DELETE | `/api/books/{id}` | 삭제 (204) | |

### 10-2. 계층별 책임
```
Controller  : HTTP ↔ DTO, @Valid, 상태 코드
Service     : @Transactional, Repository로 엔티티 조회·변경, Entity → DTO 변환(트랜잭션 안에서)
Repository  : 조회/저장 (JpaRepository + QueryRepository)
Entity      : 도메인 규칙 (검증, 상태 변경, 연관관계 편의 메소드)
```

```kotlin
@Service
@Transactional(readOnly = true)          // 클래스 기본은 읽기 전용
class BookService(…) {

	@Transactional                       // 쓰기 메소드만 따로
	fun update(id: Long, request: BookUpdateRequest): BookResponse {
		val book = bookRepository.findWithAuthorById(id) ?: throw NotFoundException("…")
		book.update(request.title, request.type, request.price)
		request.authorId?.let { book.changeAuthor(authorRepository.findByIdOrThrow(it)) }
		return BookResponse.of(book)     // save() 없음 → 커밋 시 변경 감지로 UPDATE
	}
}
```
- **`readOnly = true`**: 변경 감지 스냅샷을 생략하고 flush를 하지 않는다. 조회 성능에 유리하다.
- **수정은 `save()` 없이 변경 감지로 처리한다.** 영속 상태 엔티티를 바꾸면 커밋 시 UPDATE가 나간다.
- **Optional 대신 nullable**: `findWithAuthorById(id): Book?`, `?: throw`, 확장 함수 `findByIdOrThrow`.

### 10-3. 요청 검증 (Kotlin 특유의 함정)
```kotlin
data class BookCreateRequest(
	@field:NotBlank @field:Size(max = 200)
	val title: String,
	val type: BookType,
	@field:Min(0)
	val price: Int,
	val authorId: Long,
)
```
- **`@field:`** use-site target: 생성자 프로퍼티에 그냥 `@NotBlank`를 쓰면 예전 Kotlin에서는 **생성자 파라미터에만 붙어서 검증이 동작하지 않았다.** `@field:`로 필드에 붙이면 확실하다. (컴파일러 옵션 `-Xannotation-default-target=param-property`를 켜면 파라미터와 필드에 함께 붙는다.)
- **non-null 필드가 JSON에 없으면 Bean Validation까지 가지 못한다.** jackson-module-kotlin이 역직렬화 단계에서 실패시키고(`HttpMessageNotReadableException`), 이를 400으로 매핑한다. `@NotNull` 같은 메시지를 주고 싶다면 필드를 `T?`로 두고 `@field:NotNull`을 붙이는 방식도 있다.
- 잘못된 Enum 값(`"COMPUTERR"`)도 역직렬화 단계에서 400이 된다.

### 10-4. 예외 → HTTP 응답
`@RestControllerAdvice`에서 **`ProblemDetail`**(RFC 9457)로 통일한다.

| 예외 | 상태 |
|---|---|
| `NotFoundException` | 404 |
| `BusinessException`(중복 등) | 409 |
| `MethodArgumentNotValidException`(@Valid) | 400 + 필드별 errors |
| `HttpMessageNotReadableException`(필수 값 누락, 타입 불일치) | 400 |

### 10-5. 페이징 응답
`Page`(PageImpl)를 그대로 반환하지 말고 **`PageResponse` DTO로 감싼다.** 내부 구조가 API 스펙이 되는 것을 막는다(Spring Data도 경고한다).

---

## 11. 테스트

### 11-1. Kotlin에서 JUnit5 쓰는 법

| 문법 | 예 |
|---|---|
| 생성자 주입 | `class XTest @Autowired constructor(private val service: XService)` |
| `@BeforeAll`/`@AfterAll` | `companion object { @JvmStatic @BeforeAll fun … }` 또는 `@TestInstance(PER_CLASS)` |
| 한글 테스트 이름 | ``fun `제목이 비어 있으면 생성할 수 없다`() `` (백틱) 또는 `@DisplayName` |
| 예외 검증 | `assertThrows<IllegalArgumentException> { … }` → 반환값으로 message 검증 |
| `@Nested` | Kotlin에서는 **`inner class`**로 선언해야 한다 |
| 목 객체 필드 | `@MockitoBean lateinit var bookService: BookService` |
| MockMvc | Kotlin DSL: `mockMvc.post("/api/books") { contentType = …; content = … }.andExpect { status { isCreated() } }` |

### 11-2. `@SpringBootTest`만 쓰면 느린가? → 테스트 피라미드

**실측** (`./gradlew test`, 이 프로젝트, 테스트 클래스 단위, "컨텍스트 기동"은 로그의 `Started … in` 횟수):

| 종류 | 무엇을 띄우나 | 예제 | 시간 | 컨텍스트 기동 |
|---|---|---|---|---|
| **순수 단위 테스트** | 아무것도 없음 | `BookTest` (4개) | **0.003초** | 0 |
| **Mock 단위 테스트** (Mockito) | 아무것도 없음 (의존성은 가짜) | `BookServiceMockitoTest` (1개) | 0.16초 | 0 |
| **Mock 단위 테스트** (MockK) | 아무것도 없음 (의존성은 가짜) | `BookServiceMockKTest` (5개) | 0.6초 ※ | 0 |
| **`@DataJpaTest`** | JPA·DataSource·Repository만 | `BookQueryRepositoryTest` (2개) | 0.31초 | 1 |
| **`@WebMvcTest`** | Controller·Advice·JSON만 (Service는 Mock) | `BookControllerWebMvcTest` (2개) | 0.42초 | 1 |
| **`@SpringBootTest`** (첫 실행) | 전체 컨텍스트 | `AuthorRelationTest` (6개) | 3.7초 | 1 |
| **`@SpringBootTest`** (재사용) | 캐시된 컨텍스트 | `BookServiceTest` (6개) | **0.09초** | **0** |

> ※ Mock 라이브러리는 처음 사용될 때 바이트코드 조작 에이전트를 로딩하는 비용이 한 번 든다. 첫 번째 `@SpringBootTest` 시간에도 JVM 워밍업이 포함되어 있다. 절대값보다 **"무엇을 띄우느냐"에 따른 차이**를 보자.

**전략: 아래로 갈수록 적게**
1. **도메인 규칙은 순수 단위 테스트로** 가장 많이 검증한다. Entity의 검증, 상태 변경, 편의 메소드가 대상이다.
2. **Service의 분기와 흐름은 Mock 단위 테스트로** 검증한다. 없으면 예외, 호출 횟수, DTO 변환 같은 것들이다(§11-3).
3. **쿼리는 `@DataJpaTest`로** 검증한다. 테스트마다 롤백되고, 필요한 Bean은 `@Import(JpaConfig::class, BookQueryRepository::class)`로 추가한다.
4. **API 형식(상태 코드, JSON, 검증)은 `@WebMvcTest` + `@MockitoBean`으로** 검증한다.
5. **`@SpringBootTest`는 핵심 흐름 몇 개만** 둔다. 트랜잭션, 변경 감지, 계층 연결처럼 통합해야만 보이는 것이 대상이다.

### 11-3. Mock 테스트 작성법

**Mock** = 진짜 의존성(Repository 등) 대신 넣는 **가짜 객체**다. "이 인자로 호출되면 이 값을 돌려줘"(stub)라고 정해 두고, "몇 번 호출됐는지"(verify)를 검사한다. DB와 스프링 없이 **Service 로직만** 빠르게 검증할 수 있다.

**MockK (Kotlin에 권장)** — `testImplementation("io.mockk:mockk:1.14.11")`
```kotlin
class BookServiceMockKTest {
	private val bookRepository = mockk<BookRepository>()
	private val bookQueryRepository = mockk<BookQueryRepository>()
	private val authorRepository = mockk<AuthorRepository>()
	private val bookService = BookService(bookRepository, bookQueryRepository, authorRepository) // 직접 생성

	@Test
	fun `책을 등록하면 작가를 찾아 연결한 뒤 저장한다`() {
		val author = Author("김코틀", id = 1L)
		every { authorRepository.findById(1L) } returns Optional.of(author)           // stub
		every { bookRepository.save(any()) } answers {                                 // DB가 id를 채운 것처럼
			firstArg<Book>().let { Book(it.title, it.type, it.price, it.author, id = 10L) }
		}

		val response = bookService.create(BookCreateRequest("코틀린", BookType.COMPUTER, 20_000, 1L))

		assertThat(response.id).isEqualTo(10L)
		verify(exactly = 1) { bookRepository.save(any()) }                               // 호출 검증
	}
}
```

**Mockito (스프링에 기본 포함)**
```kotlin
@ExtendWith(MockitoExtension::class)
class BookServiceMockitoTest {
	@Mock lateinit var bookRepository: BookRepository       // 나중에 채워지므로 lateinit var
	@Mock lateinit var bookQueryRepository: BookQueryRepository
	@Mock lateinit var authorRepository: AuthorRepository
	@InjectMocks lateinit var bookService: BookService      // 생성자에 Mock들을 넣어 생성

	@Test
	fun `id로 책을 조회해 응답 DTO로 변환한다`() {
		given(bookRepository.findWithAuthorById(10L)).willReturn(book)
		…
	}
}
```

| | MockK | Mockito |
|---|---|---|
| 문법 | `every { } returns`, `verify { }` — Kotlin DSL | `given(…).willReturn(…)`, `verify(mock).…` |
| Kotlin non-null 파라미터 + `any()` | ✅ 그대로 동작한다 | ⚠️ Mockito의 `any()`가 null을 반환해 NPE가 날 수 있다 → 구체적인 값이나 `mockito-kotlin`을 쓴다 |
| final 클래스 | ✅ | ✅ (Mockito 5부터 inline mock 기본) |
| 코루틴(`suspend`) | ✅ `coEvery` | 제한적 |
| 스프링 Bean 교체 | springmockk `@MockkBean` (별도 의존성) | `@MockitoBean` (기본) |

**Kotlin에서 Mock을 쓸 때 주의할 점**
- **확장 함수는 stub할 수 없다.** `findByIdOrNull`, `findByIdOrThrow`는 확장 함수이므로 내부에서 호출하는 `findById`를 stub한다.
- **Mock 테스트에서는 id가 채워지지 않는다.** DB가 없으므로 `Entity(…, id = 10L)`처럼 id를 지정한 객체를 쓴다(Entity 생성자의 선택 인자).
- **Mock은 "호출했는가"만 검증한다.** 실제 SQL, 트랜잭션, 변경 감지는 검증되지 않는다. 예: `update()` Mock 테스트는 `save()`를 호출하지 않았다는 것까지만 확인할 수 있고, 실제 UPDATE가 나가는지는 `@SpringBootTest`(`BookServiceTest.update`)가 확인한다.
- **Mock을 너무 많이 쓰면 테스트가 구현을 복사한다.** 내부 호출 순서까지 verify하면 리팩토링할 때마다 테스트가 깨진다. **결과(반환값, 예외)를 먼저 검증**하고, verify는 "저장하지 않았다"처럼 의미 있는 부작용에만 쓴다.

### 11-4. Mock을 쓰면 컨텍스트가 공유되나?

두 가지 경우를 구분해야 한다.

| 방식 | 스프링 컨텍스트 | 공유 여부 |
|---|---|---|
| **순수 Mock** (`mockk()`, `@Mock` + MockitoExtension) | **띄우지 않는다** | 공유할 컨텍스트 자체가 없다. 가장 빠르다 |
| **`@MockitoBean`** (`@SpringBootTest`/`@WebMvcTest` 안) | 띄운다. 해당 Bean만 Mock으로 교체 | **Mock 구성이 같은 테스트끼리만 공유한다.** 구성이 다르면 새 컨텍스트 |

**실험으로 확인했다** (임시 테스트 3개, 컨텍스트 객체의 identityHashCode 출력):
```
CTX-ID A(@MockitoBean AuthorRepository) = 1285648961
CTX-ID B(@MockitoBean AuthorRepository) = 1285648961   ← A와 같은 컨텍스트 (Mock 구성이 같음)
CTX-ID PLAIN(Mock 없음)                  = 426435961    ← 다른 컨텍스트
```
- 컨텍스트 캐시의 키에는 **`@MockitoBean` 목록도 포함**된다. 그래서 테스트 클래스마다 다른 Bean을 Mock으로 바꾸면 **클래스마다 새 컨텍스트가 뜬다.** `@SpringBootTest`가 느려지는 가장 흔한 원인이다.
- 컨텍스트를 공유해도 **Mock의 stub과 호출 기록은 테스트가 끝날 때마다 초기화된다**(기본 `MockReset.AFTER`). 테스트끼리 Mock 상태가 섞이지는 않는다.
- 권장 방식:
  - Service 로직은 **순수 Mock 단위 테스트**로 한다(컨텍스트 0개).
  - 스프링과 함께 Mock이 필요하면 `@WebMvcTest`처럼 **작은 슬라이스**에서 쓴다.
  - 통합 테스트에서 Mock이 꼭 필요하면 **공통 부모 클래스에 모아서** 구성을 통일한다.

### 11-5. 그 밖에 테스트를 빠르고 튼튼하게 만드는 방법

| 방법 | 내용 | 언제 |
|---|---|---|
| **Fake 객체** | Mock 대신 **간단한 진짜 구현**을 쓴다. 예: `Map`에 저장하는 `FakeAuthorRepository`. stub 없이 상태로 검증하므로 구현이 바뀌어도 잘 안 깨진다 | 외부 API 클라이언트, 메일·결제 게이트웨이처럼 경계에 있는 인터페이스 |
| **Test Fixture** | `Book.fixture(title = "…")`처럼 기본값 팩토리를 둔다. 필드가 추가돼도 테스트 수정이 한 곳으로 줄어든다 | Entity가 많을 때 항상 |
| **도메인에 로직 모으기** | Service가 아니라 Entity 메소드에 규칙을 두면 **순수 단위 테스트로 검증할 수 있다** | 설계 단계부터 |
| **통합 테스트 설정 통일** | `abstract class IntegrationTest`에 `@SpringBootTest` 등을 모은다 → 컨텍스트 1개 | 통합 테스트가 여러 개일 때 |
| **`@DirtiesContext` 피하기** | 컨텍스트를 버리고 새로 띄운다. 대신 데이터 정리로 해결한다 | 항상 |
| **Testcontainers 싱글톤** | 컨테이너를 부모 클래스의 companion object에 두어 **JVM당 1번만** 띄운다. 로컬에서는 `withReuse(true)` + `~/.testcontainers.properties`의 `testcontainers.reuse.enable=true`로 재실행 간에도 재사용한다 | MySQL 테스트가 여러 클래스일 때 |
| **병렬 실행** | Gradle `maxParallelForks`(JVM 여러 개) 또는 JUnit `junit.jupiter.execution.parallel.enabled=true` | 테스트가 많을 때. **DB를 공유하면 데이터가 충돌하므로** 격리 전략이 먼저다 |
| **Gradle 캐시** | 코드가 안 바뀐 모듈의 테스트는 다시 돌지 않는다(`--build-cache`). 필요한 것만 `--tests '*BookTest'` | 로컬 개발 |

### 11-6. DB 정리와 Testcontainers

**DB 정리 방식**

| 방식 | 장점 | 주의 |
|---|---|---|
| 테스트에 `@Transactional` (롤백) | 빠르고 간단하다. `@DataJpaTest`의 기본 동작 | 실제 커밋이 없어서 **변경 감지·LAZY 로딩 문제를 가릴 수 있다** |
| `@AfterEach` + `deleteAllInBatch()` | 실제와 같은 커밋 흐름을 확인할 수 있다 | FK 순서대로 지운다(자식 → 부모) |

**Testcontainers(MySQL)**
- `@ServiceConnection`이 접속 정보를 자동 주입한다.
- 컨테이너는 `companion object` + `@JvmStatic`으로 클래스당 1개만 띄운다.
- `@Testcontainers(disabledWithoutDocker = true)`: Docker가 없는 환경에서는 건너뛴다.
- H2 MySQL 모드는 **완전한 MySQL이 아니다.** 방언 차이(함수, 락, 인덱스)는 Testcontainers로 확인한다.

---

## 12. 체크리스트

**설정**
- [ ] plugin.spring · plugin.jpa · **allOpen(Entity/MappedSuperclass/Embeddable)**
- [ ] kotlin-reflect · jackson-module-kotlin · `-Xjsr305=strict`
- [ ] `open-in-view: false` · `default_batch_fetch_size` · UTC 타임존 · utf8mb4
- [ ] 운영 `ddl-auto: validate/none` + Flyway

**Entity**
- [ ] `data class` 금지, `class` + `protected set`
- [ ] `@Column(nullable = false)` 명시 (Kotlin 타입만으로는 DDL에 반영되지 않는다)
- [ ] `@Enumerated(STRING)`
- [ ] 검증은 `init`과 도메인 메소드에서
- [ ] `@EnableJpaAuditing`은 별도 Config에

**연관관계**
- [ ] `@ManyToOne(fetch = LAZY)` 필수
- [ ] 양방향이면 `mappedBy` + 편의 메소드
- [ ] cascade/orphanRemoval은 부모가 생명주기를 소유할 때만
- [ ] `@ManyToMany` 대신 중간 엔티티
- [ ] Entity를 응답으로 반환하지 않기 → DTO

**조회**
- [ ] 목록 조회 시 SQL 로그로 N+1 확인
- [ ] 컬렉션 fetch join + 페이징 금지 → N:1 fetch join 또는 batch size
- [ ] fetch join은 연관관계 경로로
- [ ] 필요한 컬럼만 → Projection, `count()`는 Long

**API**
- [ ] 요청 DTO 검증은 `@field:` + `@Valid`
- [ ] 예외 → ProblemDetail 매핑 (역직렬화 실패 400 포함)
- [ ] Page를 그대로 반환하지 않기 → PageResponse

**테스트**
- [ ] 도메인은 단위 테스트 / Service 분기는 Mock 단위 테스트 / 쿼리는 `@DataJpaTest` / API는 `@WebMvcTest` / 흐름은 `@SpringBootTest`
- [ ] `@MockitoBean`은 슬라이스 테스트에서만, 통합 테스트에서는 구성을 통일
- [ ] 통합 테스트 설정을 통일해서 컨텍스트 캐시 재사용
- [ ] MySQL 고유 동작은 Testcontainers

---

## 13. Java 개발자를 위한 Kotlin 관용구

코드 리뷰에서 "Kotlin스럽지 않다"는 말을 듣는 대부분은 이 장의 내용이다. 모두 `KotlinIdiomsTest`에서 실행해 볼 수 있다.

### 13-1. Java → Kotlin 한눈에 대응표

| Java | Kotlin | 한 줄 설명 |
|---|---|---|
| `final String name = "a";` | `val name = "a"` | 타입은 추론, 기본은 불변 |
| `String s = null;` | `val s: String? = null` | null은 타입으로 표시 |
| `if (x != null) x.foo();` | `x?.foo()` | 안전 호출 |
| `x != null ? x : "기본"` | `x ?: "기본"` | 엘비스 |
| `Optional<T>` | `T?` | 타입 시스템이 대신한다 |
| `a.equals(b)` | `a == b` | `==`가 equals (null 안전) |
| `a == b` (참조) | `a === b` | 같은 객체인가 |
| `"a" + x + "b"` | `"a${x}b"` | 문자열 템플릿 |
| 텍스트 블록 `"""…"""` | `"""…""".trimIndent()` | 여러 줄 문자열 |
| 삼항 `c ? a : b` | `if (c) a else b` | if도 값을 돌려준다 |
| `switch` | `when` | 값을 돌려주는 식, 조건식도 가능 |
| Lombok `@Getter @Setter` | `var name: String` | 프로퍼티가 기본 |
| Lombok `@Value` / record | `data class` | equals·hashCode·toString·copy |
| Lombok `@Builder` | 기본값 인자 + 이름 붙인 인자 | `User(name = "김", admin = true)` |
| `static` 메소드·상수 | `companion object`, 최상위 함수, `const val` | |
| 유틸 클래스 `StringUtils.x(s)` | 확장 함수 `s.x()` | |
| 싱글톤 패턴 | `object` | 선언만으로 싱글톤 |
| `stream().filter().map().collect()` | `filter { }.map { }` | 컬렉션에 바로 |
| `instanceof` + 캐스팅 | `is` + 스마트 캐스트 | 검사 후 자동 캐스팅 |
| `throws IOException` | 없음 | checked exception이 없다 |
| `try-with-resources` | `.use { }` | `File(...).bufferedReader().use { it.readText() }` |
| `Class<T>` 파라미터 | `inline fun <reified T>` | 타입으로 바로 |
| `new` | 없음 | `Book("제목")` |

### 13-2. 스코프 함수 — 가장 많이 보이고, 가장 헷갈리는 것

**쉽게 말하면**: 객체 하나를 잡고 "블록 안에서 이것저것 하는" 함수들이다. 차이는 딱 두 가지다. **블록 안에서 객체를 `this`로 부르는지 `it`으로 부르는지**, 그리고 **무엇을 돌려주는지**.

| 함수 | 객체 참조 | 반환값 | 주 용도 | 예 |
|---|---|---|---|---|
| `apply` | `this` | 객체 자신 | **객체 설정** (빌더처럼) | `Author().apply { name = "김" }` |
| `also` | `it` | 객체 자신 | **부수 효과** (로그, 검증) | `save(x).also { log.info("저장 $it") }` |
| `let` | `it` | 블록 결과 | **null일 때 건너뛰기**, 변환 | `name?.let { book.title.contains(it) }` |
| `run` | `this` | 블록 결과 | 객체로 계산해서 값 만들기 | `author.run { age >= 19 }` |
| `with` | `this` | 블록 결과 | 이미 있는 객체로 여러 작업 | `with(author) { "$name($age)" }` |

- **외우는 법**: 자기 자신을 돌려주는 건 `apply`/`also`(설정하고 넘겨준다), 결과를 돌려주는 건 `let`/`run`/`with`.
- **팀에서 자주 쓰는 것**: `?.let`(null 처리), `apply`(객체 설정·테스트 데이터), `also`(로그). 나머지는 드물다.
- **과용 주의**: 스코프 함수를 여러 겹 중첩하면 `this`/`it`이 무엇인지 헷갈린다. 두 겹 이상이면 그냥 지역 변수를 쓰는 편이 읽기 쉽다.

### 13-3. 확장 함수

```kotlin
fun String.maskName(): String = if (length <= 1) this else first() + "*".repeat(length - 1)
"김코틀".maskName()   // "김**"
```
- 실체는 **static 메소드**다(`maskName(String receiver)`). 클래스를 실제로 바꾸는 게 아니라서 `private` 멤버에는 접근할 수 없다.
- 이 프로젝트의 예: `CrudRepository<T, ID>.findByIdOrThrow()`. 모든 Repository에 메소드가 생긴 것처럼 쓴다.
- **주의**: 아무 데나 만들면 "이 메소드 어디서 온 거지?"가 된다. 팀 규칙으로 위치(예: `common/…Extensions.kt`)를 정한다. 그리고 확장 함수는 Mock으로 대체할 수 없다(§11-3).

### 13-4. sealed class와 when — 경우의 수가 정해진 타입

```kotlin
sealed interface PaymentResult {
	data class Success(val paymentId: String) : PaymentResult
	data class Failure(val reason: String) : PaymentResult
	data object Pending : PaymentResult
}

fun message(r: PaymentResult) = when (r) {       // else 불필요
	is PaymentResult.Success -> "완료: ${r.paymentId}"   // 스마트 캐스트
	is PaymentResult.Failure -> "실패: ${r.reason}"
	PaymentResult.Pending -> "처리 중"
}
```
- **쉽게 말하면**: "이 타입의 종류는 이것들뿐"이라고 컴파일러에게 알려 주는 것이다. 새 종류를 추가하면 **처리하지 않은 `when`이 전부 컴파일 에러**가 나서 빠뜨릴 수 없다.
- Java 17의 `sealed interface` + switch 패턴 매칭과 같은 개념이다.
- 서비스 결과, 외부 API 응답, 상태 표현에 자주 쓴다. **예외 대신 결과 타입으로 실패를 표현**하는 팀도 많다.

### 13-5. 컬렉션 API

| 하고 싶은 것 | Kotlin | Java Stream |
|---|---|---|
| 거르고 바꾸기 | `filter { }.map { }` | `stream().filter().map().collect(toList())` |
| 그룹별 개수 | `groupingBy { it.type }.eachCount()` | `groupingBy(…, counting())` |
| Map으로 | `associateBy { it.id }` | `toMap(…)` |
| 합계·최대 | `sumOf { it.price }`, `maxBy { it.price }` | `mapToInt().sum()`, `max(comparing(…))` |
| 조건 첫 원소 | `firstOrNull { … }` (없으면 null) | `findFirst()` → Optional |
| 두 그룹으로 | `val (a, b) = partition { … }` | `partitioningBy` |
| 지연 처리 | `asSequence()` | Stream 자체가 지연 |

- List의 `map`/`filter`는 **단계마다 새 리스트**를 만든다. 원소가 아주 많거나 `take(n)`처럼 일찍 멈출 수 있으면 `asSequence()`로 Java Stream처럼 지연 처리한다.
- `first { }`는 없으면 예외를 던지고, `firstOrNull { }`은 null을 반환한다. Kotlin에서는 `OrNull` 버전을 쓰고 `?:`로 처리하는 것이 관례다.

### 13-6. 그 밖에 자주 보는 것

| 문법 | 쉽게 말하면 | 예 |
|---|---|---|
| `object` | 선언 자체가 싱글톤 | `object IdGenerator { fun next() … }` |
| `by lazy { }` | 처음 쓸 때 한 번만 계산(스레드 안전) | `val heavy by lazy { load() }` |
| `lateinit var` | "나중에 반드시 채울게" 약속(non-null) | 테스트의 `@MockitoBean lateinit var` |
| `value class` | 원시 타입에 이름을 붙여 뒤바뀜 방지(비용 거의 없음) | `value class BookId(val value: Long)` |
| `inline` + `reified` | 제네릭 타입을 런타임에 사용 | `list.filterIsInstance<String>()` |
| 기본값 + 이름 인자 | 빌더 패턴 대체 | `createUser(name = "김", admin = true)` |
| `try`도 식 | 결과를 바로 대입 | `val v = try { … } catch (e: X) { "기본" }` |
| `.use { }` | try-with-resources | `stream.use { it.read() }` |
| `typealias` | 긴 타입에 별명 | `typealias Handler = (Event) -> Unit` |

---

## 14. Java ↔ Kotlin 상호운용

회사 코드베이스는 **Java와 Kotlin이 섞여 있는 경우가 많다.** 서로 부를 때 알아야 할 것들이다. `InteropSample.kt`를 `JavaInteropTest.java`에서 실제로 호출해 확인했다.

### 14-1. Java에서 Kotlin 부르기

| Kotlin 선언 | Java에서 보이는 모습 | 편하게 만드는 어노테이션 |
|---|---|---|
| `val label` | `getLabel()` | `@JvmField` → 필드 `label`로 바로 |
| 기본값 인자 `greet(name, greeting = "안녕")` | 인자를 모두 넘겨야 한다 | `@JvmOverloads` → 인자 개수별 오버로딩 생성 |
| `companion object { fun create() }` | `InteropSample.Companion.create()` | `@JvmStatic` → `InteropSample.create()` |
| `companion object { const val VERSION }` | `InteropSample.VERSION` | (`const`면 그대로 static 상수) |
| 최상위 함수 `fun sum()` (파일 `InteropSample.kt`) | `InteropSampleKt.sum()` | `@file:JvmName("MathUtils")` → `MathUtils.sum()` |
| 예외를 던지는 함수 | Java가 `catch`하려 하면 컴파일 에러 | `@Throws(IOException::class)` |
| `var isActive: Boolean` | `isActive()` / `setActive()` | |
| `internal` 함수 | 이름이 변형된 public 메소드 | Java에서는 부르지 않는다 |

```java
InteropSample sample = InteropSample.create();       // @JvmStatic
sample.greet("김");                                   // @JvmOverloads 덕분에 기본값 사용
InteropSampleKt.sum(1, 2);                            // 최상위 함수
```

### 14-2. Kotlin에서 Java 부르기

- **플랫폼 타입(`String!`)**: Java 메소드의 반환값은 Kotlin이 null 여부를 모른다. 받는 쪽에서 `String?`으로 받아 처리하거나, Java 쪽에 `@Nullable`/`@NonNull`(JSpecify, JetBrains, Spring 어노테이션)을 붙인다. **Java 코드와 맞닿는 곳이 NPE가 가장 많이 나는 지점이다.**
- **getter/setter는 프로퍼티처럼**: `user.getName()` → `user.name`. 단, IntelliJ가 `getUsers()` 같은 "진짜 getter가 아닌" 메소드도 프로퍼티로 바꾸라고 제안할 수 있으니 주의한다.
- **SAM 변환**: Java의 함수형 인터페이스(`Runnable`, `Comparator`)에 람다를 바로 넘긴다. `executor.submit { … }`
- **Lombok**: Kotlin 코드는 Lombok이 생성한 getter를 **같은 모듈에서는 보지 못할 수 있다**(컴파일 순서 문제). Kotlin Lombok 컴파일러 플러그인을 쓰거나, 마주치는 Java 클래스부터 Kotlin으로 옮긴다.

### 14-3. IntelliJ 도구

| 기능 | 위치 | 언제 |
|---|---|---|
| **Show Kotlin Bytecode → Decompile** | Tools → Kotlin | "이 Kotlin 코드가 Java로는 뭐지?" 궁금할 때 (가장 유용) |
| Convert Java File to Kotlin File | Code 메뉴 | 옮기기 시작점으로. **결과는 반드시 검토** (nullable, `!!` 남발) |
| Java 코드 붙여넣기 | .kt 파일에 Java 코드를 붙여넣기 | 자동 변환을 제안한다 |

---

## 15. 코루틴

### 15-1. 코루틴이 뭔가요? (스레드와 비교)

**비유**: 식당에 직원(스레드)이 몇 명 있다.
- **스레드 방식**: 손님이 주문한 요리가 나올 때까지(= DB, 외부 API 응답 대기) 직원이 **그 테이블 옆에 서서 기다린다.** 손님이 많아지면 직원(스레드)을 계속 늘려야 하고, 직원 한 명은 비싸다(메모리 약 1MB).
- **코루틴 방식**: 주문을 넣고 요리를 기다리는 동안 **직원은 다른 테이블을 응대하다가**, 요리가 나오면 돌아와서 이어서 서빙한다. 적은 직원으로 많은 손님을 받는다.

코드로 보면 "기다리는 동안 멈춰 있다가(suspend) 이어서(resume) 실행되는 함수"다. 실행 중 스레드를 점유하지 않으므로 **10만 개를 만들어도 가볍다**(`CoroutineBasicsTest`에서 확인).

| | Java 스레드 / `CompletableFuture` | Kotlin 코루틴 |
|---|---|---|
| 기다리는 코드 | `future.get()`(스레드 블로킹) 또는 `thenApply` 체인 | 그냥 순서대로 쓴 코드 + `suspend` |
| 동시 실행 | `CompletableFuture.supplyAsync` ×2 + `allOf` | `async { }` ×2 + `await()` |
| 취소·예외 전파 | 직접 관리 | **구조적 동시성**으로 자동 |
| 비용 | 스레드당 메모리 큼 | 매우 가벼움 |

### 15-2. 기본 문법

```kotlin
suspend fun fetchPrice(): Int {       // suspend: 잠시 멈출 수 있는 함수
	delay(1_000)                      // 1초 기다림. 이 동안 스레드는 다른 일을 한다
	return 20_000
}

// 순서대로 → 2초
val a = fetchPrice()
val b = fetchPrice()

// 동시에 → 1초
coroutineScope {
	val a = async { fetchPrice() }    // 시작만 하고 다음 줄로
	val b = async { fetchPrice() }
	a.await() + b.await()             // 둘 다 끝나길 기다림
}
```

| 빌더/함수 | 쉽게 말하면 | 반환 |
|---|---|---|
| `launch { }` | "이 일 시작해 줘" (결과 필요 없음) | `Job` |
| `async { }` | "이 일 시작하고 결과는 나중에 줘" | `Deferred<T>` → `await()` |
| `coroutineScope { }` | 안에서 시작한 일이 **모두 끝나야** 빠져나온다 | 블록 결과 |
| `withContext(Dispatchers.IO) { }` | 이 블록은 다른 스레드 풀에서 실행 | 블록 결과 |
| `runBlocking { }` | 일반 코드에서 코루틴을 시작하고 끝날 때까지 **스레드를 막고** 기다림 | main, 테스트 외에는 쓰지 않는다 |

### 15-3. 구조적 동시성 (Structured Concurrency)

**쉽게 말하면**: 코루틴은 **부모-자식 관계**를 가진다.
- 부모는 자식이 **모두 끝날 때까지** 기다린다.
- 자식 하나가 **실패하면 형제들은 취소되고** 예외는 부모로 전달된다.
- 부모가 취소되면 자식도 모두 취소된다.

Java에서 스레드를 여러 개 띄웠다가 하나가 실패하면 나머지를 직접 정리해야 했던 문제를 언어 차원에서 해결한다. `CoroutineBasicsTest`의 `자식 하나가 실패하면…` 테스트에서 형제가 `"취소됨"`으로 끝나는 것을 확인했다.

> `GlobalScope.launch { }`는 부모가 없는 코루틴이라 이 보호를 받지 못한다. 실무 코드에서는 쓰지 않는다.

### 15-4. Dispatcher — 어느 스레드에서 실행할까

| Dispatcher | 용도 |
|---|---|
| `Dispatchers.Default` | CPU 계산 (정렬, 변환). 코어 수만큼의 스레드 |
| `Dispatchers.IO` | **블로킹 I/O** (JDBC/JPA, 파일, 블로킹 HTTP 클라이언트). 많은 스레드 |
| `Dispatchers.Main` | 안드로이드 UI 스레드 (서버에서는 사용하지 않음) |

**핵심 규칙**: JPA/JDBC는 스레드를 붙잡는 블로킹 호출이다. 코루틴 안에서 부를 때는 `withContext(Dispatchers.IO)` 또는 `async(Dispatchers.IO)`로 IO 스레드에서 실행한다.

### 15-5. Spring에서 쓰기

**suspend 컨트롤러** — Spring MVC도 지원한다(`kotlinx-coroutines-reactor` 필요).
```kotlin
@GetMapping("/api/dashboard")
suspend fun dashboard(): DashboardResponse = dashboardService.getDashboard()
```

**서로 관계없는 조회를 동시에** (`DashboardService`)
```kotlin
suspend fun getDashboard(): DashboardResponse = coroutineScope {
	val typeCounts = async(Dispatchers.IO) { bookService.countByType() }
	val authorCounts = async(Dispatchers.IO) { authorService.countBooksPerAuthor() }
	DashboardResponse(typeCounts.await(), authorCounts.await())
}
```
- **실측**: 각각 300ms 걸리는 조회 두 개를 **307ms**에 끝냈다(`DashboardServiceTest`). 순서대로였다면 600ms 이상이다.
- **테스트**: suspend 컨트롤러는 MockMvc에서 비동기로 처리되므로 `asyncStarted()` 확인 후 `asyncDispatch()`로 결과를 검증한다(`DashboardControllerTest`).

### 15-6. ⚠️ 코루틴 + JPA/트랜잭션 함정

**스프링 트랜잭션은 스레드(ThreadLocal)에 묶여 있다.** 코루틴이 다른 스레드로 넘어가면 트랜잭션이 따라가지 않는다.

```kotlin
tx.executeWithoutResult {
	isActualTransactionActive()                                    // true
	runBlocking { withContext(Dispatchers.IO) { isActualTransactionActive() } }   // false!
}
```
테스트(`CoroutineTransactionTest`)로 확인했다. 다른 스레드에서 한 엔티티 변경은 **변경 감지·롤백 대상이 아니다.**

**그래서 이렇게 한다**
1. `@Transactional` 메소드 **안에서** 코루틴으로 스레드를 바꾸지 않는다.
2. 동시 실행이 필요하면 `DashboardService`처럼 **바깥(트랜잭션 없는 곳)에서 코루틴을 쓰고**, 각 작업이 **각자의 트랜잭션**을 가진 서비스 메소드를 부르게 한다.
3. 영속성 컨텍스트(EntityManager)는 스레드 안전하지 않다. **엔티티 객체를 코루틴 사이에 넘기지 말고** DTO를 넘긴다.
4. 완전한 논블로킹이 필요하면 JPA 대신 **R2DBC**(Spring Data R2DBC)를 쓴다. 이 경우 `@Transactional`도 코루틴을 지원한다.

### 15-7. 언제 코루틴을 쓸까?

| 상황 | 권장 |
|---|---|
| 평범한 CRUD API (DB 1~2번 호출) | **코루틴 불필요.** 일반 함수로 충분하다 |
| 서로 관계없는 외부 API·조회 여러 개를 모아 응답 | ✅ `async`로 동시에 |
| 외부 API를 많이 호출하는 서비스 (WebClient) | ✅ suspend + `awaitBody()` |
| 대량 동시 연결 (채팅, 스트리밍) | ✅ WebFlux + 코루틴 + `Flow` |
| "스레드가 모자라서" 동시 처리량을 올리고 싶다 | JDK 21+ **가상 스레드**(`spring.threads.virtual.enabled=true`)도 검토한다. 기존 블로킹 코드를 거의 그대로 둘 수 있다 |

### 15-8. 코루틴 테스트

```kotlin
@Test
fun `async로 동시에 부르면 1초`() = runTest {       // kotlinx-coroutines-test
	val a = async { fetchPrice() }
	val b = async { fetchPrice() }
	a.await() + b.await()
	assertThat(currentTime).isEqualTo(1_000)        // 가상 시간: 실제로 1초 기다리지 않는다
}
```
- `runTest`는 `delay`를 **가상 시간**으로 처리하므로 시간이 걸리는 코드도 순식간에 테스트된다.
- MockK는 suspend 함수를 `coEvery { … } returns …`, `coVerify { … }`로 다룬다.

### 15-9. Flow (한 줄 소개)
`Flow<T>`는 **값을 여러 번 흘려보내는 suspend 버전의 Stream**이다(`flow { emit(1); emit(2) }`). WebFlux 스트리밍 응답, 이벤트 처리에서 쓴다. MVC + JPA 위주의 팀이라면 처음에는 몰라도 된다.

---

## 16. 팀 도구 · 컨벤션

| 항목 | 내용 |
|---|---|
| **코딩 컨벤션** | [Kotlin 공식 코딩 컨벤션](https://kotlinlang.org/docs/coding-conventions.html). 들여쓰기 4칸이 표준이다(이 예제는 Initializr 기본값인 탭). IntelliJ: Settings → Code Style → Kotlin → "Set from… Kotlin style guide" |
| **포매터·린터** | **ktlint**(포맷), **detekt**(정적 분석, 복잡도·코드 냄새). 대부분의 팀이 Gradle 플러그인으로 CI에서 검사한다 |
| **trailing comma** | 여러 줄 인자·파라미터 끝에 `,`를 붙인다(이 예제처럼). 줄 추가 시 diff가 한 줄로 줄어든다 |
| **Gradle** | `build.gradle.kts` (Kotlin DSL). 문법이 Groovy와 조금 다르다: `implementation("…")` 괄호와 큰따옴표 |
| **로깅** | `private val log = LoggerFactory.getLogger(javaClass)` 또는 `kotlin-logging`(`private val log = KotlinLogging.logger {}`) — 팀 표준을 따른다 |
| **테스트** | JUnit5 + AssertJ로 시작한다. 팀에 따라 **Kotest**(BDD 스타일 스펙), **MockK**를 쓴다 |
| **null 표시** | 공개 API의 반환 타입에 `T?`를 쓸지 예외를 던질지 팀 규칙이 있다(예: `find…`는 `T?`, `get…`은 예외) |

---

## 17. Java 개발자가 자주 하는 실수 Top 10

| # | 실수 | 왜 문제인가 | 이렇게 |
|---|---|---|---|
| 1 | **`!!` 남발** | Java의 NPE를 그대로 가져온다 | `?.`, `?:`, `requireNotNull`로 이유를 드러낸다 |
| 2 | **Entity에 `data class`** | 연관관계 순환, hashCode 변동 | 일반 `class` + allOpen (§2-9) |
| 3 | **`var`와 `MutableList`를 기본으로** | 어디서 바뀌는지 추적이 어렵다 | `val` + `List` 기본, 필요할 때만 가변 |
| 4 | **`lateinit` 남발** | 초기화 전에 접근하면 예외. null 체크를 숨길 뿐이다 | 생성자 주입, `by lazy`, nullable |
| 5 | **Java식 getter/setter 작성** | `fun getName()`은 Kotlin스럽지 않다 | 프로퍼티(`val name`) |
| 6 | **유틸 클래스 + static 메소드** | 호출이 장황하다 | 확장 함수 또는 최상위 함수 |
| 7 | **`Optional` 그대로 사용** | Kotlin에서는 이중 포장이다 | `T?` + `findByIdOrNull` |
| 8 | **스코프 함수 과다 중첩** | `this`/`it` 혼동 | 두 겹 이상이면 지역 변수 |
| 9 | **자동 변환 결과를 그대로 커밋** | `!!`, 플랫폼 타입, `Int`/`Int?` 오류가 남는다 | 변환 후 nullable과 val/var을 다시 판단 |
| 10 | **`@Transactional` 안에서 코루틴으로 스레드 전환** | 트랜잭션이 사라진다 | 트랜잭션 밖에서 동시 실행 (§15-6) |

---

## 18. 첫 2주 학습 로드맵

| 기간 | 목표 | 할 것 |
|---|---|---|
| **1~2일** | 문법 읽기 | 2장 · 13장을 읽고 `learning/` 테스트를 모두 실행한다. 값을 바꿔서 일부러 실패시켜 본다 |
| **3~4일** | 팀 코드 읽기 | 팀의 Entity → Service → Controller 순서로 읽는다. 모르는 문법은 **Decompile**로 Java 모습을 확인한다 |
| **5일** | 작은 기여 | 테스트 추가나 작은 버그 수정. 코드 리뷰에서 받은 "Kotlin스럽게" 코멘트를 17장 표에 추가해 나간다 |
| **2주 차 전반** | JPA/Spring 패턴 | 5~10장. 팀의 연관관계·조회 패턴을 이 문서와 비교한다 |
| **2주 차 후반** | 테스트 · 코루틴 | 11장으로 테스트 속도를 점검한다. 팀이 코루틴을 쓴다면 15장 → 팀 코드의 `suspend` 사용처를 읽는다 |
| 이후 | 심화 | 제네릭 변성(`in`/`out`), `inline` 함수, `Flow`, DSL 빌더, Kotest |

**참고 자료**
- Kotlin 공식 문서: https://kotlinlang.org/docs/home.html (특히 "Kotlin for Java developers" 성격의 비교 문서들)
- Kotlin Koans (브라우저 연습 문제): https://play.kotlinlang.org/koans
- Spring Framework Kotlin 지원 문서: https://docs.spring.io/spring-framework/reference/languages/kotlin.html
- 코루틴 가이드: https://kotlinlang.org/docs/coroutines-guide.html

---

## 19. Kafka

예제: `library-api/src/main/kotlin/.../event/`, 테스트 `KafkaEventTest` (EmbeddedKafka, Docker 불필요)

### 19-1. 한 장 요약
```
BookService (트랜잭션 안)
   └ ApplicationEventPublisher.publishEvent(BookEvent)      ← 스프링 이벤트만 발행
        └ @TransactionalEventListener(AFTER_COMMIT)          ← 커밋된 뒤에
             └ KafkaTemplate.send(topic, key=bookId, event)  ← 진짜 발행
                  └ @KafkaListener → 실패 시 재시도 토픽 → DLT
```

### 19-2. Kotlin에서 주의할 점

| 주제 | 내용 |
|---|---|
| **메시지 DTO** | `data class`가 적합하다(Entity와 반대). 필드를 추가할 땐 **기본값**을 줘야 예전 메시지도 읽힌다 |
| **역직렬화** | Kotlin data class는 기본 생성자가 없다 → `jackson-module-kotlin` 필요 |
| **Jackson 3** | Spring Boot 4는 Jackson 3(`tools.jackson`)를 쓴다. spring-kafka에서도 **`JacksonJsonSerializer` / `JacksonJsonDeserializer`**(Jackson 3용)를 쓴다. 이름이 비슷한 `JsonSerializer`는 Jackson 2용이라 클래스패스에 없으면 실패한다 |
| **`@RetryableTopic`** | `backOff = BackOff(delay = 200)` — spring-kafka의 `org.springframework.kafka.annotation.BackOff`다(spring-retry의 `Backoff`가 아니다). kapt와 충돌할 수 있어 enum 인자(`topicSuffixingStrategy`)는 생략하는 편이 안전하다 |
| **`@EnableKafka`** | 커스텀 `ConcurrentKafkaListenerContainerFactory`를 직접 정의하면 리스너가 뜨지 않을 수 있다. 설정 클래스에 **`@EnableKafka`를 명시**하면 확실하다(실제로 이 문제를 겪고 확인했다) |

### 19-3. 안전하게 쓰기 위한 설정

| 설정 | 값 | 이유 |
|---|---|---|
| `acks` | `all` | 리더 + 팔로워까지 기록돼야 성공. 유실 방지 |
| `enable.idempotence` | `true` | 재시도로 인한 중복 발행 방지 |
| **`max.block.ms`** | `5000` | 브로커가 죽었을 때 `send()`가 **호출 스레드를 60초(기본값)나 붙잡는다.** 이 값을 줄여야 API가 같이 느려지지 않는다 (테스트에서 실제로 60초 지연을 겪고 넣었다) |
| `ErrorHandlingDeserializer` | 컨슈머 | JSON이 깨진 메시지 하나 때문에 컨슈머가 무한 재시도에 빠지는 것을 막는다 |
| `auto.offset.reset` | `earliest` | 새 컨슈머 그룹이 처음부터 읽는다 |
| `트러스트 패키지` | `addTrustedPackages("com.example.library.event")` | 임의 클래스 역직렬화 차단 |

### 19-4. 트랜잭션과 메시지 발행

| 방식 | 문제 / 효과 |
|---|---|
| 서비스에서 바로 `send()` | 이후 롤백돼도 메시지는 이미 나간다 → **"등록 실패했는데 등록 이벤트가 나감"** |
| **`@TransactionalEventListener(AFTER_COMMIT)`** (이 예제) | 커밋된 뒤에만 발행. 대부분의 상황에 충분하다 |
| Outbox 패턴 | 커밋은 됐는데 발행이 실패하는 경우까지 막는다. 이벤트를 **같은 트랜잭션에서 DB 테이블에 저장**하고 별도 프로세스가 읽어 발행 |
| Kafka 트랜잭션(`KafkaTemplate` + `@Transactional`) | DB와 Kafka의 원자성은 여전히 보장되지 않는다(서로 다른 시스템) |

테스트로 확인: 서비스 호출 후 예외로 롤백시키면 컨슈머에 아무것도 도착하지 않는다.

### 19-5. 재시도와 DLT
```kotlin
@RetryableTopic(attempts = "2", backOff = BackOff(delay = 200))
@KafkaListener(topics = [BookEvent.TOPIC], groupId = "library-book-events", containerFactory = "...")
fun consume(event: BookEvent) { … }

@DltHandler
fun dlt(event: BookEvent, @Header(KafkaHeaders.ORIGINAL_TOPIC) topic: String) { … }
```
- 실패한 메시지를 **재시도 토픽**으로 보내 잠시 뒤 다시 처리한다. 메인 컨슈머가 막히지 않는 것이 블로킹 재시도와의 차이.
- 끝내 실패하면 **DLT**로 보낸다. DLT는 알림 → 수동 처리 대상으로 본다.
- 테스트에서 "수신 2회 → DLT 이동"을 로그로 확인했다.

### 19-6. 운영에서 기억할 것
- **소비는 at-least-once**다. 같은 메시지가 여러 번 올 수 있으니 **멱등하게** 처리한다(이벤트 id 또는 대상 상태 기준).
- **순서는 파티션 단위로만** 보장된다. 순서가 중요한 단위(여기선 bookId)를 **키**로 쓴다.
- **컨슈머 랙**(`kafka_consumergroup_lag`)을 모니터링한다(§24 경보 규칙에 포함).
- 처리량이 부족하면 파티션 수를 늘리고 `concurrency`(컨슈머 스레드)를 맞춘다. **컨슈머 수 > 파티션 수는 의미 없다.**
- 스키마가 바뀌면 소비 측이 먼저 배포되도록 순서를 정한다(하위 호환 필드 추가 → 발행 측 배포).

---

## 20. Redis

예제: `config/RedisConfig.kt`, `common/RedisLockService.kt`, 테스트 `RedisCacheTest`(Testcontainers, Docker 필요)

### 20-1. Kotlin + Redis 직렬화 (가장 많이 막히는 곳)
```kotlin
GenericJacksonJsonRedisSerializer.builder { JsonMapper.builder().addModule(KotlinModule.Builder().build()) }
	.enableDefaultTyping(validator)   // JSON에 @class 타입 정보를 넣는다
	.enableSpringCacheNullValueSupport()
	.build()
```
| 문제 | 원인 | 해결 |
|---|---|---|
| 캐시에서 꺼내면 `LinkedHashMap` | JSON에 타입 정보가 없다 | `enableDefaultTyping` (타입 검증기로 패키지 제한) |
| "생성자를 찾을 수 없다" | Kotlin data class엔 기본 생성자가 없다 | `KotlinModule` 등록 |
| 값이 읽기 힘든 바이트 | 기본이 JDK 직렬화 | 키는 `StringRedisSerializer`, 값은 JSON |
| 배포 후 역직렬화 실패 | DTO 필드가 바뀌었는데 옛 캐시가 남아 있다 | 캐시 이름에 버전(`bookStats:v2`)을 넣거나 배포 시 비운다 |

테스트로 확인: `BookTypeCount`(data class)를 넣었다 꺼내면 **같은 타입 · 같은 값**으로 복원되고 TTL이 걸려 있다.

### 20-2. 캐시 (`@Cacheable` / `@CacheEvict`)
```kotlin
@Cacheable(cacheNames = ["bookStats"], key = "'all'")      // 키: bookStats::all
fun countByType(): List<BookTypeCount> = …

@Transactional
@CacheEvict(cacheNames = ["bookStats"], allEntries = true)  // 통계가 바뀌면 비운다
fun create(request: BookCreateRequest): BookResponse = …
```
- **TTL을 반드시 준다**(`entryTtl`). 안 주면 영원히 남는다. 캐시별로 다르게 줄 수 있다.
- `disableCachingNullValues()`: null을 캐시하면 "없음"이 계속 유지된다.
- ⚠️ **`allEntries = true`는 즉시 반영되지 않을 수 있다.** 이 조합(Spring Data Redis 4.1 + Lettuce)에서 `create()` 직후 키가 남아 있다가 잠시 뒤 사라지는 것을 확인했다. 키를 지정한 `evict`는 즉시 반영된다. **테스트에서는 잠시 기다리거나 키 지정 방식을 쓴다.**
- 캐시 비우기는 `prefix*` 패턴으로 키를 찾아 지운다. 배치 전략을 `BatchStrategies.scan(100)`으로 명시하면 운영 Redis에서 `KEYS` 명령으로 멈추는 일을 피할 수 있다.

### 20-3. 분산 락
```kotlin
fun <T> withLock(key: String, ttl: Duration = Duration.ofSeconds(10), action: () -> T): T? {
	val token = UUID.randomUUID().toString()
	val acquired = redisTemplate.opsForValue().setIfAbsent("lock:$key", token, ttl) ?: false
	if (!acquired) return null
	return try { action() } finally {
		if (redisTemplate.opsForValue().get("lock:$key") == token) redisTemplate.delete("lock:$key")
	}
}
```
- `SET NX PX` 한 방으로 "없을 때만 세팅" → 한 명만 잡는다. **TTL 필수**(서버가 죽어도 풀린다).
- 해제할 때 **내 토큰인지 확인**한다. 남의 락을 푸는 사고 방지(엄밀히는 Lua로 비교+삭제를 원자적으로).
- 작업이 TTL보다 길어지면 락이 먼저 풀린다 → 작업을 짧게 두거나 Redisson 같은 라이브러리를 검토한다.
- 테스트로 확인: 락을 잡은 동안 두 번째 시도는 `null`, 끝나면 키가 사라지고 다시 잡을 수 있다.

### 20-4. 운영 주의
| 항목 | 내용 |
|---|---|
| `KEYS` 금지 | 운영 Redis를 멈춘다. `SCAN`을 쓴다 |
| 키 네이밍 | `서비스:도메인:식별자` 규칙을 팀에서 정한다 (`library:book:1`) |
| 캐시 스탬피드 | 인기 키 만료 시 요청이 몰린다 → TTL에 지터를 주거나 락으로 한 명만 계산 |
| 캐시 = 사라질 수 있는 것 | Redis가 비어도 서비스가 동작해야 한다 |
| 직렬화 크기 | 큰 객체를 통째로 캐시하지 않는다. 필요한 필드만 |
| 메모리 정책 | `maxmemory-policy`(예: `allkeys-lru`)를 운영 환경에 맞게 |

---

## 21. 컨테이너와 쿠버네티스

### 21-1. Dockerfile (JVM)
```dockerfile
FROM eclipse-temurin:25-jdk AS build
… ./gradlew bootJar -x test

FROM eclipse-temurin:25-jre         # 실행은 JRE로 (이미지 크기 ↓)
RUN useradd -r -u 1001 app
USER 1001                            # root로 실행하지 않는다
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/app.jar"]
```
- **`-Xmx` 대신 `-XX:MaxRAMPercentage`**: 컨테이너 메모리 limit에 맞춰 힙이 정해진다.
- 의존성 파일(`build.gradle.kts`)을 소스보다 먼저 복사하면 레이어 캐시가 살아 빌드가 빨라진다.
- 대안: Spring Boot의 `bootBuildImage`(Buildpacks), Jib — Dockerfile 없이 이미지를 만든다.

### 21-2. 프로브 (스프링 부트 액추에이터)
```yaml
management.endpoint.health.probes.enabled: true   # /actuator/health/liveness · /readiness
```
| 프로브 | 뜻 | 실패하면 |
|---|---|---|
| `startupProbe` | 기동이 끝났나 | 기동 중에는 다른 프로브를 시작하지 않는다. **JVM처럼 느린 앱에 필수** |
| `readinessProbe` | 트래픽 받을 준비가 됐나 | Service에서 빠진다(재시작 X) |
| `livenessProbe` | 살아 있나 | **재시작**한다 |

- liveness에 DB 체크를 넣지 않는다. DB가 잠깐 느리면 **전체 파드가 재시작 루프**에 빠진다.
- 무중단 종료: `preStop`으로 몇 초 쉬고(`sleep 5`) `terminationGracePeriodSeconds`를 충분히 준다. 스프링의 `server.shutdown=graceful`과 함께 쓴다.

### 21-3. 리소스 · 스케일

| 항목 | 권장 |
|---|---|
| memory request = limit | JVM은 예측 가능하게. OOMKilled를 피한다 |
| **CPU limit은 두지 않기** | CPU 스로틀링으로 지연이 튄다. request만 지정 |
| HPA | CPU 기준으로 시작. `replicas`는 **템플릿에서 빼야** HPA와 충돌하지 않는다 |
| PodDisruptionBudget | 노드 교체 중에도 최소 대수 유지 |
| RollingUpdate | `maxUnavailable: 0`, `maxSurge: 1` → 새 파드가 준비된 뒤 옛 파드 종료 |
| ConfigMap 변경 | 파드는 자동 재시작되지 않는다 → **체크섬 어노테이션**으로 재시작 유도 |

---

## 22. Helm 차트

예제: `deploy/helm/library-api/` (`helm lint` · `helm template` · `kubeconform` 검증 완료)

```
Chart.yaml        차트 버전(version) + 앱 버전(appVersion)
values.yaml       기본값
values-prod.yaml  환경별 덮어쓰기 (helm -f values.yaml -f values-prod.yaml)
templates/
  _helpers.tpl    이름 · 라벨 생성 함수 (모든 리소스가 같은 규칙을 쓰게)
  deployment.yaml service.yaml ingress.yaml configmap.yaml hpa.yaml pdb.yaml servicemonitor.yaml
  NOTES.txt       설치 후 안내 문구
```

| 알아둘 것 | 내용 |
|---|---|
| `{{ include "…" . }}` | 헬퍼 함수 호출. `nindent`로 들여쓰기를 맞춘다 |
| `{{- if … }}` | 조건부 리소스(ingress.enabled 등). `{{-`는 앞 공백 제거 |
| 값 우선순위 | `--set` > 뒤에 온 `-f` > 앞의 `-f` > `values.yaml` |
| **비밀값** | 차트에 넣지 않는다. 외부에서 만든 Secret 이름만 참조(`existingSecret`)하거나 External Secrets / Sealed Secrets |
| 이미지 태그 | `latest` 금지. CI가 **커밋 SHA**로 채운다 |
| 검증 | `helm lint` → `helm template`으로 렌더링 확인 → `kubeconform`으로 스키마 검증 (CI에 넣는다) |
| 배포 | `helm upgrade --install --atomic --wait` (실패 시 자동 롤백) |

---

## 23. ArgoCD (GitOps)

**GitOps = "Git에 있는 것이 곧 클러스터 상태"**. 배포는 `kubectl apply`가 아니라 **Git 커밋**으로 한다.

```yaml
kind: Application
spec:
  source: { repoURL: …, targetRevision: main, path: deploy/helm/library-api, helm: { valueFiles: [values.yaml, values-prod.yaml] } }
  destination: { server: https://kubernetes.default.svc, namespace: library }
  syncPolicy:
    automated: { prune: true, selfHeal: true }
    syncOptions: [CreateNamespace=true]
```
| 옵션 | 뜻 |
|---|---|
| `automated.prune` | Git에서 지운 리소스는 클러스터에서도 지운다 |
| `automated.selfHeal` | 누가 `kubectl`로 손대면 Git 상태로 되돌린다 |
| `finalizers` | Application 삭제 시 배포된 리소스까지 정리 |
| `ApplicationSet` | dev/stg/prod를 한 정의로 생성 |

**배포 흐름**: 코드 머지 → CI가 이미지 빌드·푸시 → CI가 **values의 이미지 태그를 커밋** → ArgoCD가 감지해 동기화 → 상태를 UI에서 확인.
- 롤백은 **Git revert**(또는 ArgoCD History에서 이전 리비전 선택).
- Argo Rollouts를 더하면 카나리·블루그린 배포가 된다.
- 주의: 사람이 클러스터를 직접 고치면 `selfHeal`이 되돌린다. 긴급 수정도 Git을 거치는 문화가 필요하다.

---

## 24. Prometheus · Grafana

### 24-1. 애플리케이션 쪽 (이미 설정되어 있다)
```yaml
management.endpoints.web.exposure.include: health,info,prometheus
management.metrics.tags.application: library-api   # 모든 메트릭에 붙는 공통 라벨
```
- `spring-boot-starter-actuator` + `micrometer-registry-prometheus` → `/actuator/prometheus`
- Prometheus Operator를 쓰면 **ServiceMonitor**(차트에 포함)를 만들어 스크랩 대상을 자동 등록한다.

### 24-2. 자주 쓰는 쿼리 (PromQL)

| 보고 싶은 것 | 쿼리 |
|---|---|
| 처리량 | `sum(rate(http_server_requests_seconds_count{application="library-api"}[5m])) by (uri)` |
| p95 지연 | `histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le))` |
| 에러율 | `sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count[5m]))` |
| 힙 사용률 | `jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}` |
| DB 풀 대기 | `hikaricp_connections_pending` |
| GC 일시정지 | `rate(jvm_gc_pause_seconds_sum[5m])` |
| 컨슈머 랙 | `sum(kafka_consumergroup_lag{consumergroup="library-book-events"})` |

### 24-3. 경보 규칙 (`deploy/monitoring/prometheus-rules.yaml`)
에러율 5% 초과, p95 1초 초과, 힙 90% 초과, DB 커넥션 대기, 파드 재시작 반복, 컨슈머 랙 — 6개를 `PrometheusRule`로 정의했다(스키마 검증 완료).
- `for:` 를 반드시 준다. 순간 스파이크로 알림이 울리지 않게 한다.
- `severity` 라벨로 알림 경로를 나눈다(critical → 전화, warning → 채팅).

### 24-4. Grafana
`deploy/monitoring/grafana-dashboard.json` — 처리량 / p95·p99 / 에러율 / 힙 / DB 풀 / GC / 컨슈머 랙 7개 패널.
- 대시보드도 **Git으로 관리**한다(JSON 커밋 → ConfigMap 또는 Grafana provisioning).
- 팀 표준: 첫 화면은 **RED 지표**(Rate, Errors, Duration) 세 개로 시작한다.

---

## 25. nginx (Ingress)

쿠버네티스에서는 보통 **ingress-nginx 컨트롤러**를 쓰고, 앱별 설정은 Ingress 리소스의 **어노테이션**으로 준다.

```yaml
annotations:
  nginx.ingress.kubernetes.io/proxy-body-size: "8m"
  nginx.ingress.kubernetes.io/proxy-read-timeout: "30"
  nginx.ingress.kubernetes.io/limit-rps: "100"
  cert-manager.io/cluster-issuer: letsencrypt-prod
```

| 증상 | 흔한 원인 |
|---|---|
| **502 Bad Gateway** | 파드가 준비 안 됨(readinessProbe), Service `targetPort` 불일치 |
| **504 Gateway Timeout** | 앱 응답이 `proxy-read-timeout`보다 느림 — 타임아웃을 늘리기 전에 쿼리를 본다 |
| **413** | 업로드가 `proxy-body-size`(기본 1m) 초과 |
| 클라이언트 IP가 전부 같음 | `use-forwarded-headers`, `externalTrafficPolicy: Local` |
| 배포 중 502 | `preStop` 대기 + `terminationGracePeriodSeconds` 부족 |

- 컨트롤러 설정은 `deploy/nginx/ingress-nginx-values.yaml`(로그 JSON, keepalive, 메트릭 ServiceMonitor).
- 카나리 배포: `nginx.ingress.kubernetes.io/canary: "true"` + `canary-weight: "10"`.
- 쿠버네티스가 아닌 VM 앞단 예시는 `deploy/nginx/nginx.conf`(업스트림, rate limit, X-Forwarded-For, 헬스체크 로그 제외).
