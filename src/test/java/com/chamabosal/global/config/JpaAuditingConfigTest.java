package com.chamabosal.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.data.auditing.DateTimeProvider;

class JpaAuditingConfigTest {

    @Test
    void 감사_시각은_KST_기준_마이크로초_단위로_잘린다() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-30T00:00:00.123456789Z"), ClockConfig.KST);
        DateTimeProvider provider = new JpaAuditingConfig().auditingDateTimeProvider(clock);

        assertThat(provider.getNow()).contains(LocalDateTime.of(2026, 10, 30, 9, 0, 0, 123_456_000));
    }
}
