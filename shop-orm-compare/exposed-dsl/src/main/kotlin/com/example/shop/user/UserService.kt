package com.example.shop.user

import com.example.shop.common.error.DuplicateEmailException
import com.example.shop.common.error.NotFoundException
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true) // 기본은 레플리카. 쓰기 메서드만 @Transactional로 소스
class UserService(private val userRepository: UserRepository) {

	/** 이메일 중복이면 409 */
	@Transactional
	fun register(request: UserRegisterRequest): UserResponse =
		try {
			userRepository.insert(request.email, request.name)
		} catch (e: DuplicateKeyException) { // Repository 프록시를 지나며 번역된 예외
			throw DuplicateEmailException(request.email)
		}

	fun get(id: Long): UserResponse = userRepository.findById(id) ?: throw NotFoundException("User", id)
}
