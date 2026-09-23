package com.example.library.learning

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 학습용: Java 개발자가 Kotlin 코드 리뷰에서 가장 먼저 마주치는 관용구(idiom)들.
 * 각 테스트 위 주석의 "Java:"는 같은 일을 Java로 할 때의 모습이다.
 */
class KotlinIdiomsTest {

	// ── 스코프 함수: 객체 하나를 두고 블록 안에서 무언가 할 때 ──

	data class Author(var name: String = "", var age: Int = 0)

	@Test
	fun `apply는 설정 후 객체 자신을, let은 블록의 결과를 돌려준다`() {
		// Java: Author a = new Author(); a.setName("김"); a.setAge(30);
		val author = Author().apply { // this = 대상 객체, 반환 = 대상 객체
			name = "김코틀"
			age = 30
		}
		// Java: String upper = author != null ? author.getName().toUpperCase() : null;
		val length: Int? = author.name.let { it.length } // it = 대상 객체, 반환 = 블록의 마지막 값
		val logged = author.also { println("생성됨: $it") } // it = 대상, 반환 = 대상 (부수 효과용)
		val summary = with(author) { "$name($age)" } // this = 대상, 반환 = 블록 결과
		val adult = author.run { age >= 19 } // this = 대상, 반환 = 블록 결과

		assertThat(author.name).isEqualTo("김코틀")
		assertThat(length).isEqualTo(3)
		assertThat(logged).isSameAs(author)
		assertThat(summary).isEqualTo("김코틀(30)")
		assertThat(adult).isTrue()
	}

	// ── 확장 함수: 기존 클래스에 메소드를 "붙인 것처럼" 쓰기 (Java의 static 유틸 메소드) ──

	private fun String.maskName(): String = if (length <= 1) this else first() + "*".repeat(length - 1)

	@Test
	fun `확장 함수는 유틸 클래스를 대신한다`() {
		// Java: StringUtils.maskName("김코틀")
		assertThat("김코틀".maskName()).isEqualTo("김**")
	}

	// ── sealed class + when: 경우의 수가 정해진 타입 (Java 17 sealed interface + switch 패턴과 비슷) ──

	sealed interface PaymentResult {
		data class Success(val paymentId: String) : PaymentResult
		data class Failure(val reason: String) : PaymentResult
		data object Pending : PaymentResult
	}

	private fun message(result: PaymentResult): String = when (result) { // else가 없어도 된다: 모든 경우를 컴파일러가 확인
		is PaymentResult.Success -> "결제 완료: ${result.paymentId}" // 스마트 캐스트로 바로 paymentId 사용
		is PaymentResult.Failure -> "결제 실패: ${result.reason}"
		PaymentResult.Pending -> "처리 중"
	}

	@Test
	fun `sealed와 when은 빠진 경우를 컴파일 에러로 알려준다`() {
		assertThat(message(PaymentResult.Success("P-1"))).isEqualTo("결제 완료: P-1")
		assertThat(message(PaymentResult.Pending)).isEqualTo("처리 중")
	}

	@Test
	fun `when과 if는 값을 돌려주는 식이다`() {
		// Java: 삼항 연산자 또는 switch 표현식
		val score = 85
		val grade = when {
			score >= 90 -> "A"
			score >= 80 -> "B"
			else -> "C"
		}
		val pass = if (score >= 60) "합격" else "불합격" // Kotlin에는 삼항 연산자가 없다
		assertThat(grade).isEqualTo("B")
		assertThat(pass).isEqualTo("합격")
	}

	// ── object: 싱글톤 (Java의 private 생성자 + static INSTANCE) ──

	object IdGenerator {
		private var seq = 0L
		fun next(): Long = ++seq
	}

	@Test
	fun `object는 선언만으로 싱글톤이다`() {
		val first = IdGenerator.next()
		assertThat(IdGenerator.next()).isEqualTo(first + 1)
	}

	// ── 동등성: == 는 equals, === 는 참조 비교 ──

	@Test
	fun `Kotlin의 ==는 Java의 equals다`() {
		val a = String(charArrayOf('a', 'b'))
		val b = String(charArrayOf('a', 'b'))
		assertThat(a == b).isTrue() // Java: a.equals(b)  (null 안전)
		assertThat(a === b).isFalse() // Java: a == b  (같은 객체인가)
	}

	// ── 문자열 템플릿과 여러 줄 문자열 ──

	@Test
	fun `문자열 템플릿`() {
		val name = "김코틀"
		val books = listOf("A", "B")
		// Java: "작가 " + name + ", 책 " + books.size() + "권"  또는 String.format
		assertThat("작가 $name, 책 ${books.size}권").isEqualTo("작가 김코틀, 책 2권")
		val sql = """
			select *
			from book
		""".trimIndent() // Java 15+ 텍스트 블록과 같은 역할
		assertThat(sql).isEqualTo("select *\nfrom book")
	}

	// ── 컬렉션 API: Stream 없이 바로 ──

	data class Book(val title: String, val type: String, val price: Int)

	private val books = listOf(
		Book("코틀린", "IT", 30_000),
		Book("스프링", "IT", 40_000),
		Book("경제", "ECON", 20_000),
	)

	@Test
	fun `컬렉션 함수는 stream과 collect 없이 바로 쓴다`() {
		// Java: books.stream().filter(b -> b.getPrice() >= 30000).map(Book::getTitle).collect(toList())
		assertThat(books.filter { it.price >= 30_000 }.map { it.title }).containsExactly("코틀린", "스프링")
		// Java: Collectors.groupingBy(Book::getType, Collectors.counting())
		assertThat(books.groupingBy { it.type }.eachCount()).isEqualTo(mapOf("IT" to 2, "ECON" to 1))
		// Java: Collectors.toMap(Book::getTitle, b -> b)
		assertThat(books.associateBy { it.title }.keys).containsExactly("코틀린", "스프링", "경제")
		assertThat(books.sumOf { it.price }).isEqualTo(90_000)
		assertThat(books.maxBy { it.price }.title).isEqualTo("스프링")
		assertThat(books.firstOrNull { it.type == "NONE" }).isNull() // Optional 대신 null
		val (it, others) = books.partition { it.type == "IT" }
		assertThat(it).hasSize(2)
		assertThat(others).hasSize(1)
	}

	@Test
	fun `큰 데이터는 asSequence로 지연 처리한다`() {
		// List의 map/filter는 단계마다 새 리스트를 만든다. Sequence는 Java Stream처럼 한 원소씩 흘려보낸다
		val firstThree = (1..1_000_000).asSequence()
			.map { it * 2 }
			.filter { it % 3 == 0 }
			.take(3)
			.toList()
		assertThat(firstThree).containsExactly(6, 12, 18)
	}

	// ── 지연 초기화: by lazy ──

	class Config {
		var loadCount = 0
		val heavy: String by lazy { // 처음 접근할 때 한 번만 계산 (스레드 안전)
			loadCount++
			"loaded"
		}
	}

	@Test
	fun `by lazy는 처음 쓸 때 한 번만 계산한다`() {
		val config = Config()
		assertThat(config.loadCount).isEqualTo(0)
		repeat(3) { config.heavy }
		assertThat(config.loadCount).isEqualTo(1)
	}

	// ── value class: 원시 타입에 이름을 붙여 실수를 막는다 (런타임 비용 거의 없음) ──

	@JvmInline
	value class AuthorId(val value: Long)

	@JvmInline
	value class BookId(val value: Long)

	private fun findBook(id: BookId): String = "book-${id.value}"

	@Test
	fun `value class는 같은 Long이라도 다른 타입으로 구분한다`() {
		val bookId = BookId(1)
		// findBook(AuthorId(1)) ← 컴파일 에러: Long끼리 뒤바뀌는 실수를 막는다
		assertThat(findBook(bookId)).isEqualTo("book-1")
	}

	// ── reified: 제네릭 타입을 런타임에 알 수 있다 (Java의 Class<T> 파라미터 대신) ──

	private inline fun <reified T> List<Any>.onlyOf(): List<T> = filterIsInstance<T>()

	@Test
	fun `reified 제네릭은 Class 파라미터 없이 타입으로 거른다`() {
		// Java: filter(list, String.class)
		val mixed: List<Any> = listOf(1, "a", 2, "b")
		assertThat(mixed.onlyOf<String>()).containsExactly("a", "b")
	}

	// ── 기본값 인자 + 이름 붙인 인자: 빌더 패턴이 필요 없다 ──

	private fun createUser(name: String, age: Int = 0, admin: Boolean = false) = "$name/$age/$admin"

	@Test
	fun `이름 붙인 인자로 빌더를 대신한다`() {
		// Java: User.builder().name("김").admin(true).build()
		assertThat(createUser(name = "김", admin = true)).isEqualTo("김/0/true")
	}

	// ── 예외: checked exception이 없다 ──

	private fun readConfig(): String = throw java.io.IOException("파일 없음") // throws 선언 불필요

	@Test
	fun `Kotlin에는 checked exception이 없고 try도 식이다`() {
		val value = try {
			readConfig()
		} catch (e: java.io.IOException) {
			"default"
		}
		assertThat(value).isEqualTo("default")
	}
}
