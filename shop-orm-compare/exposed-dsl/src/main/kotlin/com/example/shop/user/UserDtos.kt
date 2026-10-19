package com.example.shop.user

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

data class UserRegisterRequest(
	@field:Email @field:NotBlank @field:Size(max = 255)
	val email: String,
	@field:NotBlank @field:Size(max = 50)
	val name: String,
)

data class UserResponse(
	val id: Long,
	val email: String,
	val name: String,
	val createdAt: LocalDateTime,
)
