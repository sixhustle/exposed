package com.example.shop.user

import com.example.shop.common.error.DuplicateEmailException
import com.example.shop.common.error.NotFoundException
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 엔티티는 트랜잭션 밖으로 내보내지 않는다. 서비스가 트랜잭션 안에서 응답(data class)으로 바꾼다 */
@Service
@Transactional(readOnly = true) // 기본은 레플리카. 쓰기 메서드만 @Transactional로 소스
class UserService(private val userRepository: UserRepository) {

	/** 이메일 중복이면 409 */
	@Transactional
	fun register(request: UserRegisterRequest): UserResponse =
		try {
			userRepository.insert(request.email, request.name).toResponse()
		} catch (e: DuplicateKeyException) {
			throw DuplicateEmailException(request.email)
		}

	fun get(id: Long): UserResponse = userRepository.findById(id)?.toResponse() ?: throw NotFoundException("User", id)
}
