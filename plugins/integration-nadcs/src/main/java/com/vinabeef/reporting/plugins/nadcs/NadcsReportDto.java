package com.vinabeef.reporting.plugins.nadcs;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/** Wire format expected by NADCS. Hand-written JSON keeps the plugin free of extra dependencies. */
record NadcsReportDto(String farmCode, LocalDate periodFrom, LocalDate periodTo, List<CaseDto> cases) {

    /** caseRef = health_record.record_id, so NADCS receipts can be traced back to the source record. */
    record CaseDto(String caseRef, String animalId, String location, String diseaseCode,
                   String diagnosis, LocalDateTime detectedAt) {
        String toJson() {
            return "{\"caseRef\":" + q(caseRef) + ",\"animalId\":" + q(animalId) + ",\"location\":" + q(location)
                    + ",\"diseaseCode\":" + q(diseaseCode) + ",\"diagnosis\":" + q(diagnosis)
                    + ",\"detectedAt\":" + q(detectedAt) + "}";
        }
    }

    String toJson() {
        return "{\"farmCode\":" + q(farmCode) + ",\"periodFrom\":" + q(periodFrom) + ",\"periodTo\":" + q(periodTo)
                + ",\"caseCount\":" + cases.size()
                + ",\"cases\":[" + cases.stream().map(CaseDto::toJson).collect(Collectors.joining(",")) + "]}";
    }

    private static String q(Object v) {
        if (v == null) return "null";
        String s = v.toString().replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
        return "\"" + s + "\"";
    }
}
