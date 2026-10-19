package com.example.shop.user

import com.example.shop.common.exposed.AuditedLongEntity
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.LongEntityClass

class UserEntity(id: EntityID<Long>) : AuditedLongEntity(id, Users) {
	companion object : LongEntityClass<UserEntity>(Users)

	var email by Users.email
	var name by Users.name

	fun toResponse() = UserResponse(id.value, email, name, createdAt)
}
