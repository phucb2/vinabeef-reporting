package com.vinabeef.reporting.kernel.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

class ReportPeriodTest {

    @Test
    void parsesNamedPeriods() {
        assertThat(ReportPeriod.parse("2026-Q1")).isEqualTo(new ReportPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31)));
        assertThat(ReportPeriod.parse("2026-H2")).isEqualTo(new ReportPeriod(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 31)));
        assertThat(ReportPeriod.parse("2026-02").to()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(ReportPeriod.parse("2026-W01").from()).isEqualTo(LocalDate.of(2025, 12, 29));   // ISO week
    }

    @Test
    void rejectsUnknownForms() {
        assertThatThrownBy(() -> ReportPeriod.parse("Q1-2026")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Granularity.parse("fortnight")).hasMessageContaining("QUARTER");
    }
}
