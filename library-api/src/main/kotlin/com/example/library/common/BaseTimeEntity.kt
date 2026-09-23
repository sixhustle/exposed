package com.example.library.common

import jakarta.persistence.Column
import jakarta.persistence.EntityListeners
import jakarta.persistence.MappedSuperclass
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

/**
 * 생성/수정 시각을 자동으로 채우는 공통 부모 클래스.
 * JpaConfig의 @EnableJpaAuditing이 있어야 동작한다.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseTimeEntity {

	@CreatedDate
	@Column(nullable = false, updatable = false)
	var createdAt: LocalDateTime = LocalDateTime.MIN
		protected set

	@LastModifiedDate
	@Column(nullable = false)
	var updatedAt: LocalDateTime = LocalDateTime.MIN
		protected set
}
