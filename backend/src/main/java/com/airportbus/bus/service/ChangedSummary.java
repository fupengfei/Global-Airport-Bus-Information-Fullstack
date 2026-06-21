package com.airportbus.bus.service;

import java.util.List;

/** 字段级变更摘要(相对上一版本):标量 old→new + 变更的子表名。 */
public record ChangedSummary(List<FieldChange> scalars, List<String> changedSubtables) {
    public record FieldChange(String field, String oldValue, String newValue) {}
}
