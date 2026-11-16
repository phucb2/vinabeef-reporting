package com.vinabeef.reporting.kernel.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** One report row: column key -> typed value (String, Long, BigDecimal, LocalDate, LocalDateTime, Boolean). */
public final class Row {
    private final Map<String, Object> values = new LinkedHashMap<>();

    public Object get(String key)               { return values.get(key); }
    public void put(String key, Object value)   { values.put(key, value); }
    public Map<String, Object> asMap()          { return Collections.unmodifiableMap(values); }

    @Override public String toString()          { return values.toString(); }
}
