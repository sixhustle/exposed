package com.example.library.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.BatchStrategies
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.cache.RedisCacheWriter
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.time.Duration

/**
 * Redis 설정.
 *
 * Kotlin에서 가장 많이 겪는 문제: 캐시에서 꺼낼 때 "생성자를 못 찾는다"거나 LinkedHashMap이 나오는 것.
 * - 기본 직렬화는 JDK 직렬화(Serializable 필요) → JSON 직렬화로 바꾼다.
 * - JSON에는 타입 정보가 없으므로 GenericJacksonJsonRedisSerializer가 @class 필드로 타입을 함께 저장한다.
 * - Kotlin data class를 되살리려면 jackson-module-kotlin이 필요하다 (이 프로젝트에는 이미 있다).
 * - 캐시에 담는 DTO는 필드를 바꾸면 기존 캐시와 모양이 달라진다 → 캐시 이름에 버전을 넣거나 배포 시 비운다.
 */
@Configuration
@EnableCaching
class RedisConfig {

	/**
	 * Kotlin data class를 되살리려면 KotlinModule이 필요하다 (기본 생성자가 없기 때문).
	 * enableDefaultTyping: JSON 안에 @class 타입 정보를 넣어 어떤 타입으로 되돌릴지 알게 한다.
	 * 타입 검증기(PolymorphicTypeValidator)로 우리 패키지만 허용해 임의 클래스 역직렬화를 막는다.
	 */
	private fun jsonSerializer(): GenericJacksonJsonRedisSerializer {
		val validator = tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator.builder()
			.allowIfBaseType(Any::class.java)
			.allowIfSubType("com.example.library")
			.allowIfSubType("java.util")
			.allowIfSubType("java.time")
			.build()
		return GenericJacksonJsonRedisSerializer.builder { JsonMapper.builder().addModule(KotlinModule.Builder().build()) }
			.enableDefaultTyping(validator)
			.enableSpringCacheNullValueSupport()
			.build()
	}

	@Bean
	fun redisTemplate(connectionFactory: RedisConnectionFactory): RedisTemplate<String, Any> =
		RedisTemplate<String, Any>().apply {
			setConnectionFactory(connectionFactory)
			keySerializer = StringRedisSerializer() // 키를 사람이 읽을 수 있게 (기본은 JDK 직렬화라 깨져 보인다)
			valueSerializer = jsonSerializer()
			hashKeySerializer = StringRedisSerializer()
			hashValueSerializer = jsonSerializer()
		}

	@Bean
	@ConditionalOnProperty(name = ["spring.cache.type"], havingValue = "redis")
	fun cacheManager(connectionFactory: RedisConnectionFactory): RedisCacheManager {
		val config = RedisCacheConfiguration.defaultCacheConfig()
			.entryTtl(Duration.ofMinutes(10)) // TTL을 반드시 준다. 안 주면 영원히 남는다
			.disableCachingNullValues() // null을 캐시하면 "없음"이 계속 유지된다
			.serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer()))
			.serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer()))

		// allEntries=true(캐시 전체 비우기)는 "prefix*" 패턴으로 키를 찾아 지운다.
		// 어떤 배치 전략을 쓰는지 명시해 두는 편이 안전하다 (SCAN: 운영 Redis를 멈추지 않는 방식).
		val writer = RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory, BatchStrategies.scan(100))
		return RedisCacheManager.builder(writer)
			.cacheDefaults(config)
			.withCacheConfiguration("bookStats", config.entryTtl(Duration.ofMinutes(1))) // 캐시별 TTL
			.build()
	}
}
