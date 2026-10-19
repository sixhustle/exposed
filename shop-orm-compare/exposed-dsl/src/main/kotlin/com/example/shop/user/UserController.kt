package com.example.shop.user

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/users")
class UserController(private val userService: UserService) {

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	fun register(@Valid @RequestBody request: UserRegisterRequest): UserResponse = userService.register(request)

	@GetMapping("/{id}")
	fun get(@PathVariable id: Long): UserResponse = userService.get(id)
}
