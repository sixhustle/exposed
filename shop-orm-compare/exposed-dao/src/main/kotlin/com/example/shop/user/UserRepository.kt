package com.example.shop.user

import com.example.shop.common.exposed.flushChanges
import org.springframework.stereotype.Repository

@Repository
class UserRepository {
	/** new {}는 SQL을 내지 않는다. flushChanges()에서 INSERT → UNIQUE 위반은 여기서 DuplicateKeyException (번역) */
	fun insert(email: String, name: String): UserEntity =
		UserEntity.new {
			this.email = email
			this.name = name
		}.also { flushChanges() }

	fun findById(id: Long): UserEntity? = UserEntity.findById(id)
}
