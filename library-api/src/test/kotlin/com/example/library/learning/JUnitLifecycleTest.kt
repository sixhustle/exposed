package com.example.library.learning

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows

/**
 * 학습용: JUnit5 생명주기 어노테이션을 Kotlin에서 쓰는 방법.
 *
 * 실행 순서: beforeAll → (beforeEach → 테스트 → afterEach) × 테스트 수 → afterAll
 */
class JUnitLifecycleTest {

	// JUnit은 @BeforeAll/@AfterAll 메소드가 static이기를 요구한다.
	// Kotlin에는 static 키워드가 없으므로 companion object 안에 두고 @JvmStatic을 붙여
	// "진짜 Java static 메소드"로 컴파일되게 한다. @JvmStatic이 없으면 JUnit이 실행하지 못한다.
	companion object {
		private val log = mutableListOf<String>()

		@JvmStatic
		@BeforeAll
		fun beforeAll() {
			log += "beforeAll"
		}

		@JvmStatic
		@AfterAll
		fun afterAll() {
			println("실행 순서: $log")
		}
	}

	@BeforeEach
	fun beforeEach() {
		log += "beforeEach"
	}

	@AfterEach
	fun afterEach() {
		log += "afterEach"
	}

	@Test
	@DisplayName("beforeAll은 모든 테스트보다 먼저 한 번 실행된다")
	fun beforeAllRunsFirst() {
		assertThat(log.first()).isEqualTo("beforeAll")
	}

	@Test
	fun `백틱으로 감싸면 공백이 들어간 한글 테스트 이름도 쓸 수 있다`() {
		// assertThrows<T> { } : 블록이 T 예외를 던지는지 검증하고, 그 예외를 반환한다
		val e = assertThrows<IllegalArgumentException> { require(false) { "잘못된 값" } }
		assertThat(e.message).isEqualTo("잘못된 값")
	}

	/**
	 * 대안: @TestInstance(PER_CLASS) 를 붙이면 테스트 클래스 인스턴스를 하나만 만들므로
	 * @BeforeAll 을 companion object 없이 일반 메소드로 쓸 수 있다.
	 * (@Nested 는 비-static 내부 클래스여야 하므로 Kotlin에서는 반드시 inner class 로 선언한다)
	 */
	@Nested
	@TestInstance(TestInstance.Lifecycle.PER_CLASS)
	inner class PerClassLifecycle {
		private var setUpCount = 0

		@BeforeAll
		fun setUp() {
			setUpCount++
		}

		@Test
		fun beforeAllWithoutJvmStatic() {
			assertThat(setUpCount).isEqualTo(1)
		}
	}
}
