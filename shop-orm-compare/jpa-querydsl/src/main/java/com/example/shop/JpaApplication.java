package com.example.shop;

import com.example.shop.common.ShopClock;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** JPA + QueryDSL 구현. 엔티티 + 영속성 컨텍스트(더티 체킹·지연 로딩), 동적 쿼리는 QueryDSL */
@SpringBootApplication
@EnableJpaAuditing(dateTimeProviderRef = "shopDateTimeProvider")
public class JpaApplication {

    public static void main(String[] args) {
        SpringApplication.run(JpaApplication.class, args);
    }

    @Bean
    JPAQueryFactory jpaQueryFactory(EntityManager em) {
        return new JPAQueryFactory(em);
    }

    /** @CreatedDate·@LastModifiedDate도 같은 시각 기준(UTC, 마이크로초)으로 */
    @Bean
    DateTimeProvider shopDateTimeProvider() {
        return () -> Optional.of(ShopClock.now());
    }
}
