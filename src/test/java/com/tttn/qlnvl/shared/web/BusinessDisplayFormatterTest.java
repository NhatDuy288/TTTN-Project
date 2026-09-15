package com.tttn.qlnvl.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BusinessDisplayFormatterTest {
    private final BusinessDisplayFormatter formatter =
            new BusinessDisplayFormatter("Asia/Ho_Chi_Minh");

    @Test
    void formatsUtcInstantInVietnamBusinessTime() {
        assertThat(formatter.dateTime(Instant.parse("2026-09-15T07:15:00Z")))
                .isEqualTo("15/09/2026 14:15");
    }

    @Test
    void formatsMoneyWithVietnameseSeparatorsWithoutForcedTrailingZeros() {
        assertThat(formatter.money(new BigDecimal("50000.0000"))).isEqualTo("50.000");
        assertThat(formatter.money(new BigDecimal("1250.5000"))).isEqualTo("1.250,5");
    }
}
