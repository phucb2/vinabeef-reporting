package com.vinabeef.reporting.kernel.core;

import com.vinabeef.reporting.kernel.spi.ColumnEnricher;
import com.vinabeef.reporting.kernel.spi.IntegrationSinkPlugin;
import com.vinabeef.reporting.kernel.spi.Plugin;
import com.vinabeef.reporting.kernel.spi.PluginContext;
import com.vinabeef.reporting.kernel.spi.ReportDefinition;
import com.vinabeef.reporting.kernel.spi.ReportExporterPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Plugin Manager and Registry of the microkernel.
 * Lookups are by String key, so the kernel has no switch/if-else per format, report or sink (OCP).
 */
public final class PluginRegistry {

    private final Map<String, ReportDefinition> definitions;
    private final Map<String, List<ColumnEnricher>> enrichers;
    private final Map<String, ReportExporterPlugin> exporters;
    private final Map<String, IntegrationSinkPlugin> sinks;

    public PluginRegistry(Collection<ReportDefinition> definitions,
                          Collection<ColumnEnricher> enrichers,
                          Collection<ReportExporterPlugin> exporters,
                          Collection<IntegrationSinkPlugin> sinks) {
        this.definitions = index("report definition", definitions);
        this.exporters   = index("exporter", exporters);
        this.sinks       = index("sink", sinks);
        index("enricher", enrichers); // duplicate check only
        this.enrichers = enrichers.stream()
                .collect(Collectors.groupingBy(ColumnEnricher::appliesTo, LinkedHashMap::new, Collectors.toList()));
        this.sinks.values().forEach(s -> findDefinition(s.sourceReportType())); // fail fast on misconfiguration
    }

    /** Discover all plugins on the classpath (META-INF/services/...) and initialise them. */
    public static PluginRegistry discover(ClassLoader classLoader, PluginContext context) {
        PluginRegistry registry = new PluginRegistry(
                load(ReportDefinition.class, classLoader),
                load(ColumnEnricher.class, classLoader),
                load(ReportExporterPlugin.class, classLoader),
                load(IntegrationSinkPlugin.class, classLoader));
        registry.all().forEach(p -> p.init(context));
        return registry;
    }

    public ReportDefinition findDefinition(String reportType) { return find("report type", definitions, reportType); }
    public ReportExporterPlugin findExporter(String format)   { return find("format", exporters, normalize(format)); }
    public IntegrationSinkPlugin findSink(String sinkId)       { return find("sink", sinks, sinkId); }

    public List<ColumnEnricher> enrichersFor(String reportType) {
        return enrichers.getOrDefault(reportType, List.of());
    }

    public Set<String> reportTypes()      { return Collections.unmodifiableSet(definitions.keySet()); }
    public Set<String> supportedFormats() { return Collections.unmodifiableSet(exporters.keySet()); }
    public Set<String> sinkIds()          { return Collections.unmodifiableSet(sinks.keySet()); }

    public void shutdown() { all().forEach(Plugin::destroy); }

    // ------------------------------------------------------------------
    private List<Plugin> all() {
        List<Plugin> all = new ArrayList<>();
        all.addAll(definitions.values());
        enrichers.values().forEach(all::addAll);
        all.addAll(exporters.values());
        all.addAll(sinks.values());
        return all;
    }

    private static <T> List<T> load(Class<T> type, ClassLoader cl) {
        List<T> list = new ArrayList<>();
        ServiceLoader.load(type, cl).forEach(list::add);
        return list;
    }

    private static <T extends Plugin> Map<String, T> index(String kind, Collection<T> plugins) {
        Map<String, T> map = new LinkedHashMap<>();
        for (T p : plugins) {
            T previous = map.putIfAbsent(p.key(), p);
            if (previous != null) {
                throw new IllegalStateException("Duplicate " + kind + " key '" + p.key() + "': "
                        + previous.getClass().getName() + " and " + p.getClass().getName());
            }
        }
        return Collections.unmodifiableMap(map);
    }

    private static <T> T find(String kind, Map<String, T> map, String key) {
        T plugin = key == null ? null : map.get(key);
        if (plugin == null) throw new PluginNotFoundException(kind, key, map.keySet());
        return plugin;
    }

    private static String normalize(String format) {
        return format == null ? null : format.trim().toLowerCase();
    }
}
