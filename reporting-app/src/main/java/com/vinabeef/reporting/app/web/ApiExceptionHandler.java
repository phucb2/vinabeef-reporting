package com.vinabeef.reporting.app.web;

import com.vinabeef.reporting.kernel.core.PluginNotFoundException;
import com.vinabeef.reporting.kernel.core.ReportGenerationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Kernel exceptions → HTTP status (matches the Interfaces table: 400 bad input, 500 export failure). */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({PluginNotFoundException.class, IllegalArgumentException.class})
    ProblemDetail badRequest(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(ReportGenerationException.class)
    ProblemDetail generationFailed(ReportGenerationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Report could not be generated. Please retry or contact support.");
    }
}
