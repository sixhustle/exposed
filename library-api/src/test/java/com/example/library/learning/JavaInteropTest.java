package com.example.library.learning;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 학습용: Java에서 Kotlin 클래스(InteropSample.kt)를 호출하면 어떻게 보이는지 확인한다.
 * 기존 Java 코드와 Kotlin 코드가 한 프로젝트에 섞여 있는 회사에서 자주 만나는 상황이다.
 */
class JavaInteropTest {

	@Test
	void jvmStaticAndConst() {
		InteropSample sample = InteropSample.create();          // @JvmStatic
		assertThat(InteropSample.VERSION).isEqualTo("1.0");     // const val
		assertThat(InteropSample.Companion.create()).isNotNull(); // @JvmStatic 없이도 이 형태로는 부를 수 있다
		assertThat(sample.label).isEqualTo("interop");          // @JvmField
	}

	@Test
	void jvmOverloads() {
		InteropSample sample = new InteropSample();
		assertThat(sample.greet("김")).isEqualTo("안녕, 김!");               // 기본값 2개 사용
		assertThat(sample.greet("김", "반가워")).isEqualTo("반가워, 김!");
		assertThat(sample.greet("김", "반가워", "~")).isEqualTo("반가워, 김~");
	}

	@Test
	void throwsAnnotation() {
		InteropSample sample = new InteropSample();
		assertThatThrownBy(() -> sample.load("")).isInstanceOf(IOException.class);
		try {
			assertThat(sample.load("a.txt")).isEqualTo("loaded:a.txt"); // @Throws 덕분에 Java가 IOException을 catch 할 수 있다
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	@Test
	void topLevelFunction() {
		assertThat(InteropSampleKt.sum(1, 2)).isEqualTo(3); // 파일명 + Kt 클래스의 static 메소드
	}
}
