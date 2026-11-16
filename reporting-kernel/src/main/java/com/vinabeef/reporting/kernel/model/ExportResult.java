package com.vinabeef.reporting.kernel.model;

public record ExportResult(String fileName, String contentType, byte[] content) {}
