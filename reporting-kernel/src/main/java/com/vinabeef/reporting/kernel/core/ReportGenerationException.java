package com.vinabeef.reporting.kernel.core;

/** Query or rendering failed. Mapped to HTTP 500 by the web layer. */
public class ReportGenerationException extends RuntimeException {
    public ReportGenerationException(String message, Throwable cause) { super(message, cause); }
}
