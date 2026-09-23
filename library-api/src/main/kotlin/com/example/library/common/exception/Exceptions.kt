package com.example.library.common.exception

class NotFoundException(message: String) : RuntimeException(message)

class BusinessException(message: String) : RuntimeException(message)
