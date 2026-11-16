package com.vinabeef.reporting.plugins.nadcs;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Thin HTTPS client for NADCS (JDK HttpClient, TLS by default). */
class NadcsApiClient {

    record Response(int status, String body, String submissionId) {
        boolean isSuccess()   { return status >= 200 && status < 300; }
        /** Retry on throttling and server errors; 4xx (bad payload/auth) will not get better by retrying. */
        boolean isRetryable() { return status == 429 || status >= 500; }
    }

    private final HttpClient http;
    private final URI endpoint;
    private final String apiKey;
    private final Duration timeout;

    NadcsApiClient(URI baseUrl, String apiKey, Duration timeout) {
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
        this.endpoint = baseUrl.resolve("/api/v1/surveillance/reports");
        this.apiKey = apiKey;
        this.timeout = timeout;
    }

    Response submit(NadcsReportDto dto) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("X-API-Key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(dto.toJson()))
                .build();
        HttpResponse<String> res = http.send(request, HttpResponse.BodyHandlers.ofString());
        return new Response(res.statusCode(), res.body(), res.headers().firstValue("X-Submission-Id").orElse(null));
    }
}
