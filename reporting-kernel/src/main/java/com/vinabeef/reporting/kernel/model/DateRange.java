package com.vinabeef.reporting.kernel.model;

import java.time.LocalDate;

public record DateRange(LocalDate from, LocalDate to) {
    public static DateRange singleDay(LocalDate day) { return new DateRange(day, day); }
}
