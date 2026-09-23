package com.example.library.config

import com.example.library.event.BookEvent
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer
import org.springframework.kafka.support.serializer.JacksonJsonSerializer

/**
 * Kafka 설정. 핵심은 "메시지를 JSON으로 주고받되, 깨진 메시지에 막히지 않게" 하는 것.
 */
@Configuration
@EnableKafka // @KafkaListener 처리를 켠다 (스프링 부트 자동 설정이 꺼져 있을 때를 대비해 명시)
class KafkaConfig(
	@Value("\${spring.kafka.bootstrap-servers}") private val bootstrapServers: String,
	// 카프카가 없는 환경(일반 테스트)에서는 리스너를 띄우지 않는다
	@Value("\${app.kafka.listener-enabled:false}") private val listenerEnabled: Boolean,
) {

	@Bean
	fun bookEventKafkaTemplate(): KafkaTemplate<String, BookEvent> {
		val props = mapOf(
			ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
			ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
			ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JacksonJsonSerializer::class.java, // Jackson 3용
			ProducerConfig.ACKS_CONFIG to "all", // 리더 + 팔로워까지 기록되어야 성공으로 본다 (유실 방지)
			ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to true, // 재시도로 인한 중복 발행 방지
			// 브로커가 응답하지 않을 때 send()가 호출 스레드를 붙잡는 최대 시간. 기본 60초는 너무 길다
			ProducerConfig.MAX_BLOCK_MS_CONFIG to 5_000,
		)
		return KafkaTemplate(DefaultKafkaProducerFactory(props))
	}

	@Bean
	fun bookEventConsumerFactory(): ConsumerFactory<String, BookEvent> {
		val delegate = JacksonJsonDeserializer(BookEvent::class.java).apply {
			addTrustedPackages("com.example.library.event") // 아무 클래스나 역직렬화하지 않도록 제한
		}
		val props = mapOf<String, Any>(
			ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
			ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
			ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false, // 처리 후 커밋 (스프링이 관리)
		)
		// ErrorHandlingDeserializer: JSON이 깨진 메시지 때문에 컨슈머가 무한 재시도에 빠지는 것을 막는다
		return DefaultKafkaConsumerFactory(
			props,
			ErrorHandlingDeserializer(StringDeserializer()),
			ErrorHandlingDeserializer(delegate),
		)
	}

	@Bean
	fun bookEventListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, BookEvent> =
		ConcurrentKafkaListenerContainerFactory<String, BookEvent>().apply {
			setConsumerFactory(bookEventConsumerFactory())
			setAutoStartup(listenerEnabled)
		}
}
