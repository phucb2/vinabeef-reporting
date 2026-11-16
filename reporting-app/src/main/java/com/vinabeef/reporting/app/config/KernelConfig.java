package com.vinabeef.reporting.app.config;

import com.vinabeef.reporting.infrastructure.alert.LoggingAdminAlertNotifier;
import com.vinabeef.reporting.infrastructure.cache.InMemorySnapshotCache;
import com.vinabeef.reporting.infrastructure.jdbc.JdbcReportDataGateway;
import com.vinabeef.reporting.kernel.api.ReportingFacade;
import com.vinabeef.reporting.kernel.core.PluginRegistry;
import com.vinabeef.reporting.kernel.core.ReportAggregationService;
import com.vinabeef.reporting.kernel.port.AdminAlertNotifier;
import com.vinabeef.reporting.kernel.port.DashboardSnapshotCache;
import com.vinabeef.reporting.kernel.port.ReportDataGateway;
import com.vinabeef.reporting.kernel.spi.PluginContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Composition root: the ONLY place that knows concrete classes.
 * Plugins are not listed here — they are discovered from the classpath, so adding one
 * means adding a Maven dependency, not editing this file.
 */
@Configuration
public class KernelConfig {

    @Bean
    Clock clock() {
        return Clock.system(ZoneId.of("Asia/Ho_Chi_Minh"));
    }

    /** Exposes "reporting.plugins.*" properties to plugins, e.g. reporting.plugins.nadcs.base-url. */
    @Bean
    PluginContext pluginContext(Environment env, Clock clock) {
        return new PluginContext() {
            @Override public Optional<String> config(String key) {
                return Optional.ofNullable(env.getProperty("reporting.plugins." + key));
            }
            @Override public Clock clock() { return clock; }
        };
    }

    @Bean(destroyMethod = "shutdown")
    PluginRegistry pluginRegistry(PluginContext context) {
        return PluginRegistry.discover(getClass().getClassLoader(), context);
    }

    /** spring.datasource points at the READ REPLICA: this service never writes business tables. */
    @Bean
    ReportDataGateway reportDataGateway(DataSource dataSource, Clock clock,
                                        @Value("${reporting.query-timeout-seconds:30}") int timeout) {
        return new JdbcReportDataGateway(dataSource, clock, timeout);
    }

    @Bean DashboardSnapshotCache snapshotCache() { return new InMemorySnapshotCache(); }
    @Bean AdminAlertNotifier adminAlertNotifier() { return new LoggingAdminAlertNotifier(); }

    @Bean
    ReportingFacade reportingFacade(PluginRegistry registry, ReportDataGateway gateway,
                                    DashboardSnapshotCache cache, AdminAlertNotifier notifier) {
        return new ReportAggregationService(registry, gateway, cache, notifier);
    }
}
