package com.example.library.common

import com.example.library.common.exception.NotFoundException
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.findByIdOrNull

/**
 * Optional 대신 Kotlin 타입으로: 없으면 404로 이어지는 NotFoundException을 던진다.
 */
inline fun <reified T : Any, ID : Any> CrudRepository<T, ID>.findByIdOrThrow(id: ID): T =
	findByIdOrNull(id) ?: throw NotFoundException("${T::class.simpleName}(id=$id)를 찾을 수 없습니다")
