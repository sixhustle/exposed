package com.example.shop.user

import com.example.shop.common.exposed.AuditableLongIdTable

/** Flyway V1 스키마의 users를 매핑한다 (DDL을 만들지 않는다) */
object Users : AuditableLongIdTable("users") {
	val email = varchar("email", 255).uniqueIndex("uk_users_email")
	val name = varchar("name", 50)
}
