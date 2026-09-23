package com.example.library.learning

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 학습용: companion object 와 data class 가 실제로 어떻게 동작하는지 테스트로 확인한다.
 */
class KotlinBasicsTest {

	// ── val / var ──

	@Test
	fun `val은 다시 대입할 수 없지만 가리키는 객체의 내용은 바뀔 수 있다`() {
		val list = mutableListOf(1) // list = mutableListOf() ← 컴파일 에러 (재대입 불가)
		list.add(2) // 객체 내부 변경은 가능 → val은 "참조가 고정"이지 "불변 객체"가 아니다
		var count = 0
		count += 1 // var는 재대입 가능
		assertThat(list).containsExactly(1, 2)
		assertThat(count).isEqualTo(1)
	}

	// ── mutable / immutable 컬렉션 ──

	@Test
	fun `List는 읽기 전용, MutableList는 변경 가능`() {
		val readOnly: List<String> = listOf("A", "B") // add/remove 메소드 자체가 없다
		val mutable: MutableList<String> = mutableListOf("A", "B")
		mutable.add("C")
		// readOnly.add("C") ← 컴파일 에러

		assertThat(readOnly).hasSize(2)
		assertThat(mutable).containsExactly("A", "B", "C")
	}

	@Test
	fun `읽기 전용 List로 넘겨도 원본이 바뀌면 같이 바뀐다 (진짜 불변은 아니다)`() {
		val origin = mutableListOf("A")
		val view: List<String> = origin // 같은 객체를 "읽기 전용 타입"으로 볼 뿐
		origin.add("B")

		assertThat(view).containsExactly("A", "B") // view도 바뀌었다
		val snapshot = origin.toList() // 복사본을 만들면 이후 변경과 분리된다
		origin.add("C")
		assertThat(snapshot).containsExactly("A", "B")
	}

	@Test
	fun `불변 객체는 바꾸지 않고 바뀐 새 객체를 만든다`() {
		data class Price(val amount: Int) // 모든 프로퍼티가 val → 불변 객체

		val before = Price(1_000)
		val after = before.copy(amount = 2_000)

		assertThat(before.amount).isEqualTo(1_000) // 원본은 그대로
		assertThat(after.amount).isEqualTo(2_000)
		val added = listOf(1, 2) + 3 // 읽기 전용 List도 + 연산으로 "새 리스트"를 만든다
		assertThat(added).containsExactly(1, 2, 3)
	}

	// ── nullable ──

	private fun findName(id: Long): String? = if (id == 1L) "김코틀" else null

	@Test
	fun `nullable을 다루는 연산자들`() {
		val found: String? = findName(1L)
		val missing: String? = findName(2L)

		assertThat(missing?.length).isNull() // ?.  null이면 호출하지 않고 null
		assertThat(missing ?: "이름 없음").isEqualTo("이름 없음") // ?:  null일 때 기본값
		assertThat(found?.let { "안녕, $it" }).isEqualTo("안녕, 김코틀") // ?.let  null이 아닐 때만 실행
		if (found != null) {
			assertThat(found.length).isEqualTo(3) // 스마트 캐스트: if 안에서는 String으로 취급
		}
		assertThat(found!!.length).isEqualTo(3) // !!  "절대 null 아님" 단언 — null이면 NPE
		org.junit.jupiter.api.assertThrows<NullPointerException> { missing!!.length }
	}

	@Test
	fun `requireNotNull과 엘비스 throw로 null을 예외로 바꾼다`() {
		val e = org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
			requireNotNull(findName(2L)) { "작가가 없습니다" }
		}
		assertThat(e.message).isEqualTo("작가가 없습니다")
		assertThat(findName(1L) ?: throw IllegalStateException()).isEqualTo("김코틀")
	}

	// ── 주 생성자(primary constructor) ──

	class Member(
		val name: String, // val/var 가 붙으면 → 프로퍼티(필드 + getter)가 된다
		age: Int, // 아무것도 없으면 → 생성자 인자일 뿐 (init과 프로퍼티 초기화에서만 사용 가능)
		val role: String = "USER", // 기본값 인자 → Java의 생성자 오버로딩을 대신한다
	) {
		val isAdult: Boolean = age >= 19 // 인자로 받은 age를 이용해 다른 프로퍼티를 만든다

		init { // 생성 시점에 실행되는 블록 (검증에 사용)
			require(name.isNotBlank()) { "이름은 비어 있을 수 없습니다" }
		}

		constructor(name: String) : this(name, age = 0) // 부생성자: 반드시 주 생성자를 호출(this(...))해야 한다
	}

	@Test
	fun `주 생성자의 val은 프로퍼티, 표시 없는 인자는 생성에만 쓰인다`() {
		val member = Member(name = "김코틀", age = 20) // 이름 붙인 인자, role은 기본값

		assertThat(member.name).isEqualTo("김코틀")
		assertThat(member.role).isEqualTo("USER")
		assertThat(member.isAdult).isTrue()
		// member.age ← 컴파일 에러: age는 프로퍼티가 아니다
		assertThat(Member("부생성자").isAdult).isFalse()
		org.junit.jupiter.api.assertThrows<IllegalArgumentException> { Member(" ", 20) } // init 검증
	}

	// ── backing property: 안에서는 바꿀 수 있고, 밖에는 읽기 전용으로만 보여준다 ──

	class Shelf {
		private val _books = mutableListOf<String>() // 실제 데이터 (backing property, 관례상 _ 접두사)
		val books: List<String> // 외부 공개용: 읽기 전용 타입
			get() = _books

		private var _count = 0
		val count: Int
			get() = _count

		fun add(title: String) {
			_books.add(title) // 변경은 클래스 안의 메소드로만
			_count++
		}
	}

	@Test
	fun `backing property는 외부에 읽기 전용만 노출한다`() {
		val shelf = Shelf()
		shelf.add("코틀린")

		assertThat(shelf.books).containsExactly("코틀린")
		assertThat(shelf.count).isEqualTo(1)
		// shelf.books.add("x")  ← 컴파일 에러: List에는 add가 없다
		// shelf.count = 5       ← 컴파일 에러: val에는 setter가 없다
	}

	// ── data class: equals/hashCode/toString/copy/componentN 을 자동 생성 ──

	data class Point(val x: Int, val y: Int)

	class PlainPoint(val x: Int, val y: Int)

	@Test
	fun `data class는 값이 같으면 같은 객체로 본다`() {
		assertThat(Point(1, 2)).isEqualTo(Point(1, 2)) // equals 자동 생성
		assertThat(PlainPoint(1, 2)).isNotEqualTo(PlainPoint(1, 2)) // 일반 class는 참조 비교
		assertThat(Point(1, 2).toString()).isEqualTo("Point(x=1, y=2)")
	}

	@Test
	fun `copy로 일부 값만 바꾼 새 객체를 만들고 구조 분해할 수 있다`() {
		val moved = Point(1, 2).copy(y = 5)
		val (x, y) = moved // component1(), component2()
		assertThat(x to y).isEqualTo(1 to 5)
	}

	@Test
	fun `data class의 equals는 주 생성자 프로퍼티만 비교한다`() {
		data class Tagged(val id: Long) {
			var memo: String = "" // body 프로퍼티는 equals/hashCode/toString 대상이 아니다
		}
		val a = Tagged(1).apply { memo = "A" }
		val b = Tagged(1).apply { memo = "B" }
		assertThat(a).isEqualTo(b)
	}

	// ── companion object: 클래스당 하나뿐인 객체. Java의 static 자리를 대신한다 ──

	class Money private constructor(val amount: Long) {
		companion object {
			const val MAX = 1_000_000L // const val → Java static final 상수

			fun of(amount: Long): Money { // 정적 팩토리: 생성 규칙을 한 곳에 모은다
				require(amount in 0..MAX) { "금액 범위 초과" }
				return Money(amount)
			}
		}
	}

	@Test
	fun `companion object의 함수는 클래스 이름으로 호출한다`() {
		assertThat(Money.of(1_000).amount).isEqualTo(1_000)
		assertThat(Money.MAX).isEqualTo(1_000_000)
	}
}
