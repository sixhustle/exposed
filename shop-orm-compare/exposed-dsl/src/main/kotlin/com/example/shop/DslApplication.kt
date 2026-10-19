package com.example.shop

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/** Exposed DSL 구현. 테이블 → ResultRow → 불변 data class. 쓰기는 모두 명시적인 SQL 문장 */
@SpringBootApplication
class DslApplication

fun main(args: Array<String>) {
	runApplication<DslApplication>(*args)
}
