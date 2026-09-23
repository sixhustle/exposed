package com.example.library.common

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID

/**
 * Redis 분산 락 (여러 대의 서버 중 한 대만 작업하게 만들기).
 *
 * 동작
 * - SET key value NX PX ttl : 키가 없을 때만 세팅 → 성공한 한 명만 락을 잡는다.
 * - TTL을 반드시 준다. 서버가 죽어도 락이 자동으로 풀린다.
 * - 해제할 때는 "내가 잡은 락인지" 토큰을 확인한다. 남의 락을 푸는 사고를 막는다.
 *
 * 한계
 * - 작업이 TTL보다 오래 걸리면 락이 먼저 풀린다 → 작업을 짧게 두거나 갱신(lease) 로직이 필요하다.
 * - 엄격한 상호 배제가 필요하면 Redisson 같은 라이브러리나 DB 락을 검토한다.
 */
@Component
class RedisLockService(
	private val redisTemplate: RedisTemplate<String, Any>,
) {

	fun <T> withLock(key: String, ttl: Duration = Duration.ofSeconds(10), action: () -> T): T? {
		val lockKey = "lock:$key"
		val token = UUID.randomUUID().toString()
		val acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, token, ttl) ?: false
		if (!acquired) return null // 다른 서버가 이미 작업 중

		return try {
			action()
		} finally {
			// 내가 잡은 락일 때만 지운다 (원자성이 중요하면 Lua 스크립트로 비교 후 삭제)
			if (redisTemplate.opsForValue().get(lockKey) == token) {
				redisTemplate.delete(lockKey)
			}
		}
	}
}
