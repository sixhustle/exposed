package com.example.shop

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/** Exposed DAO 구현. 엔티티 + 트랜잭션 범위 identity map + 지연 로딩. 집계와 원자적 증가만 DSL */
@SpringBootApplication
class DaoApplication

fun main(args: Array<String>) {
	runApplication<DaoApplication>(*args)
}
