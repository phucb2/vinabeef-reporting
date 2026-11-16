package com.vinabeef.reporting.plugins.nadcs;

import com.vinabeef.reporting.kernel.model.DateRange;
import com.vinabeef.reporting.kernel.model.ReportDataSet;
import com.vinabeef.reporting.kernel.model.Row;

import java.time.LocalDateTime;
import java.util.List;

/** ReportDataSet (generic) -> NADCS schema (fixed, external). Only this class changes if NADCS changes. */
class NadcsPayloadMapper {

    private final String farmCode;

    NadcsPayloadMapper(String farmCode) { this.farmCode = farmCode; }

    NadcsReportDto toDto(ReportDataSet data, DateRange range) {
        List<NadcsReportDto.CaseDto> cases = data.rows().stream().map(this::toCase).toList();
        return new NadcsReportDto(farmCode, range.from(), range.to(), cases);
    }

    private NadcsReportDto.CaseDto toCase(Row r) {
        return new NadcsReportDto.CaseDto(
                (String) r.get("record_id"),
                (String) r.get("rfid"),
                (String) r.get("barn"),
                (String) r.get("disease_code"),
                (String) r.get("diagnosis"),
                (LocalDateTime) r.get("detected_at"));
    }
}
