package com.vinabeef.reporting.infrastructure.jdbc;

import com.vinabeef.reporting.kernel.core.ReportGenerationException;
import com.vinabeef.reporting.kernel.model.ColumnDef;
import com.vinabeef.reporting.kernel.model.ReportDataSet;
import com.vinabeef.reporting.kernel.model.ReportFilter;
import com.vinabeef.reporting.kernel.model.Row;
import com.vinabeef.reporting.kernel.port.ReportDataGateway;
import com.vinabeef.reporting.kernel.spi.ReportDefinition;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runs any ReportDefinition against the READ REPLICA (constraint: never lock gate/health writes).
 * Values are normalised by ColumnType so plugins always see the same Java types:
 * STRING→String, INTEGER→Long, DECIMAL→BigDecimal, DATE→LocalDate, DATETIME→LocalDateTime, BOOLEAN→Boolean.
 */
public class JdbcReportDataGateway implements ReportDataGateway {

    private final DataSource replica;
    private final Clock clock;
    private final int queryTimeoutSeconds;

    public JdbcReportDataGateway(DataSource replica, Clock clock, int queryTimeoutSeconds) {
        this.replica = replica;
        this.clock = clock;
        this.queryTimeoutSeconds = queryTimeoutSeconds;
    }

    @Override
    public ReportDataSet execute(ReportDefinition def, ReportFilter filter) {
        NamedSql sql = NamedSql.parse(def.sql());
        Map<String, Object> params = def.params(filter);
        try (Connection con = replica.getConnection()) {
            con.setReadOnly(true);
            try (PreparedStatement ps = con.prepareStatement(sql.jdbcSql())) {
                ps.setQueryTimeout(queryTimeoutSeconds);
                sql.bind(ps, params);
                try (ResultSet rs = ps.executeQuery()) {
                    List<Row> rows = new ArrayList<>();
                    while (rs.next()) rows.add(mapRow(rs, def.columns()));
                    return new ReportDataSet(def.reportType(), def.title(), def.columns(), rows, clock.instant());
                }
            }
        } catch (SQLException e) {
            throw new ReportGenerationException("Query failed for report " + def.reportType(), e);
        }
    }

    private static Row mapRow(ResultSet rs, List<ColumnDef> columns) throws SQLException {
        Row row = new Row();
        for (ColumnDef c : columns) {
            Object value = switch (c.type()) {
                case STRING -> rs.getString(c.key());
                case INTEGER -> { long v = rs.getLong(c.key()); yield rs.wasNull() ? null : v; }
                case DECIMAL -> rs.getBigDecimal(c.key());
                case BOOLEAN -> { boolean v = rs.getBoolean(c.key()); yield rs.wasNull() ? null : v; }
                case DATE -> rs.getObject(c.key(), LocalDate.class);
                case DATETIME -> { Timestamp t = rs.getTimestamp(c.key()); yield t == null ? null : t.toLocalDateTime(); }
            };
            row.put(c.key(), value);
        }
        return row;
    }
}
