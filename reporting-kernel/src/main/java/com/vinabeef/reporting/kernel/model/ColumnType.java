package com.vinabeef.reporting.kernel.model;

/** Primitive cell types. Closed set on purpose: exporters format cells by type, not by report. */
public enum ColumnType { STRING, INTEGER, DECIMAL, DATE, DATETIME, BOOLEAN }
