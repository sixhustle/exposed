package com.example.library.learning

import java.io.IOException

/**
 * 학습용: Java 코드에서 Kotlin 클래스를 편하게 부르게 해 주는 어노테이션들.
 * 실제 호출은 src/test/java/.../JavaInteropTest.java 에서 확인한다.
 */
class InteropSample {

	@JvmField // getter 없이 Java에서 필드로 바로 접근: sample.label
	val label: String = "interop"

	// 기본값 인자는 Java에 없는 기능 → @JvmOverloads가 인자 개수별 오버로딩 메소드를 만들어 준다
	@JvmOverloads
	fun greet(name: String, greeting: String = "안녕", suffix: String = "!"): String = "$greeting, $name$suffix"

	// Kotlin에는 checked exception이 없어 throws 선언도 없다 → Java가 catch 할 수 있게 @Throws로 알려 준다
	@Throws(IOException::class)
	fun load(path: String): String = if (path.isBlank()) throw IOException("경로 없음") else "loaded:$path"

	companion object {
		const val VERSION = "1.0" // Java: InteropSample.VERSION (static final 상수)

		@JvmStatic // Java: InteropSample.create() — 없으면 InteropSample.Companion.create()
		fun create(): InteropSample = InteropSample()
	}
}

// 최상위 함수: Java에서는 파일이름Kt 클래스의 static 메소드로 보인다 → InteropSampleKt.sum(1, 2)
fun sum(a: Int, b: Int): Int = a + b
