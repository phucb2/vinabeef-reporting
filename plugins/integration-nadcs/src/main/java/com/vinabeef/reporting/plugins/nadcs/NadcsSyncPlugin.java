package com.vinabeef.reporting.plugins.nadcs;

import com.vinabeef.reporting.kernel.model.DateRange;
import com.vinabeef.reporting.kernel.model.ReportDataSet;
import com.vinabeef.reporting.kernel.model.SyncResult;
import com.vinabeef.reporting.kernel.spi.IntegrationSinkPlugin;
import com.vinabeef.reporting.kernel.spi.PluginContext;

import java.net.URI;
import java.time.Duration;

/**
 * Sends suspected outbreak cases to the NADCS REST API (FR-12).
 * The kernel gives it a generic ReportDataSet; mapping to the NADCS schema lives here only.
 */
public class NadcsSyncPlugin implements IntegrationSinkPlugin {

    private NadcsApiClient client;
    private NadcsPayloadMapper mapper;
    private ExponentialBackoffRetry retry;

    @Override public String sinkId()           { return "NADCS"; }
    @Override public String sourceReportType() { return "outbreak-cases"; }

    @Override
    public void init(PluginContext ctx) {
        URI baseUrl = URI.create(ctx.requireConfig("nadcs.base-url"));
        String apiKey = ctx.requireConfig("nadcs.api-key");
        String farmCode = ctx.requireConfig("nadcs.farm-code");
        int maxAttempts = Integer.parseInt(ctx.config("nadcs.max-attempts").orElse("5"));

        this.client = new NadcsApiClient(baseUrl, apiKey, Duration.ofSeconds(10));
        this.mapper = new NadcsPayloadMapper(farmCode);
        this.retry = new ExponentialBackoffRetry(maxAttempts, Duration.ofSeconds(2), 2.0);
    }

    @Override
    public SyncResult transmit(ReportDataSet data, DateRange range) {
        NadcsReportDto dto = mapper.toDto(data, range);
        ExponentialBackoffRetry.Outcome<NadcsApiClient.Response> outcome = retry.execute(
                () -> client.submit(dto),
                NadcsApiClient.Response::isRetryable);

        if (outcome.error() != null) {
            return SyncResult.failed(outcome.attempts(), outcome.error().getMessage());
        }
        NadcsApiClient.Response response = outcome.value();
        return response.isSuccess()
                ? SyncResult.success(outcome.attempts(), response.submissionId())
                : SyncResult.failed(outcome.attempts(), "HTTP " + response.status() + ": " + response.body());
    }
}
