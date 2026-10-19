package com.example.shop.user

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Repository

fun ResultRow.toUserResponse() = UserResponse(this[Users.id].value, this[Users.email], this[Users.name], this[Users.createdAt])

@Repository
class UserRepository {
	/** 일반 INSERT. UNIQUE 위반은 번역 인터셉터가 DuplicateKeyException으로 (insertIgnore는 FK 위반도 삼킨다) */
	fun insert(email: String, name: String): UserResponse =
		Users.insert {
			it[Users.email] = email
			it[Users.name] = name
		}.resultedValues!!.single().toUserResponse()

	fun findById(id: Long): UserResponse? = Users.selectAll().where { Users.id eq id }.singleOrNull()?.toUserResponse()

	fun existsById(id: Long): Boolean = !Users.select(Users.id).where { Users.id eq id }.empty()
}
