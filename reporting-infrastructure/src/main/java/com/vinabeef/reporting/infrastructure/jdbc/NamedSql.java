package com.vinabeef.reporting.infrastructure.jdbc;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts ":name" parameters to JDBC "?" (ignores PostgreSQL "::type" casts and quoted literals). */
final class NamedSql {

    private static final Pattern PARAM = Pattern.compile("'[^']*'|::|:([a-zA-Z_][a-zA-Z0-9_]*)");

    private final String jdbcSql;
    private final List<String> names;

    private NamedSql(String jdbcSql, List<String> names) {
        this.jdbcSql = jdbcSql;
        this.names = names;
    }

    static NamedSql parse(String sql) {
        List<String> names = new ArrayList<>();
        StringBuilder out = new StringBuilder();
        Matcher m = PARAM.matcher(sql);
        while (m.find()) {
            String replacement = m.group(1) == null ? m.group() : "?";
            if (m.group(1) != null) names.add(m.group(1));
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return new NamedSql(out.toString(), List.copyOf(names));
    }

    String jdbcSql() { return jdbcSql; }
    List<String> names() { return names; }

    void bind(PreparedStatement ps, Map<String, Object> params) throws SQLException {
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            if (!params.containsKey(name)) throw new IllegalArgumentException("Missing SQL parameter :" + name);
            Object v = params.get(name);
            if (v == null) ps.setNull(i + 1, Types.VARCHAR);
            else ps.setObject(i + 1, v);
        }
    }
}
