package com.example.shop.common;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * 애플리케이션의 유일한 "현재 시각". 세 프로젝트가 같은 규칙(UTC, 마이크로초)을 쓴다.
 * - UTC: JVM 타임존과 무관하게 같은 값 (보고서 01-21)
 * - 마이크로초 절사: DATETIME(6)에 저장한 값과 응답 값이 같도록. MySQL은 7자리 이상을 "반올림"한다 (보고서 02 결함)
 */
public final class ShopClock {

    private ShopClock() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }
}
