package com.vinabeef.reporting.plugins.definitions;

import com.vinabeef.reporting.kernel.model.Granularity;
import com.vinabeef.reporting.kernel.model.ReportFilter;
import com.vinabeef.reporting.kernel.spi.ReportDefinition;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

/**
 * Standard parameters shared by all SQL reports (schema v2, see db/schema-v2.sql):
 *   :from, :toExclusive  timestamptz bounds of the half-open range, at farm local midnight
 *   :barnId              null = all barns
 *   :bucket              date_trunc unit ('day' … 'year') for reports that group by time
 * Timestamps are bound as OffsetDateTime so PostgreSQL can use the indexes on *_at columns.
 */
public abstract class AbstractSqlReportDefinition implements ReportDefinition {

    /** Farm local time: day, month and quarter boundaries are taken in this zone. */
    public static final ZoneId FARM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Optional barn filter on any barn_id column, e.g. barnFilter("h.barn_id"). */
    protected static String barnFilter(String column) {
        return "(CAST(:barnId AS varchar) IS NULL OR " + column + " = :barnId)";
    }

    /** Granularity used when the caller gives none. Reports that do not bucket by time ignore it. */
    protected Granularity defaultGranularity() { return Granularity.MONTH; }

    @Override
    public final Map<String, Object> params(ReportFilter filter) {
        Map<String, Object> p = new HashMap<>();
        p.put("from", filter.from().atStartOfDay(FARM_ZONE).toOffsetDateTime());
        p.put("toExclusive", filter.toExclusive().atStartOfDay(FARM_ZONE).toOffsetDateTime());
        p.put("barnId", filter.barnId());
        p.put("bucket", filter.granularityOr(defaultGranularity()).sqlUnit());
        p.putAll(extraParams(filter));
        return p;
    }

    protected Map<String, Object> extraParams(ReportFilter filter) { return Map.of(); }
}
