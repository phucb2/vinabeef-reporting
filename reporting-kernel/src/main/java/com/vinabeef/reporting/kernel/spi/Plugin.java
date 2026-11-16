package com.vinabeef.reporting.kernel.spi;

/**
 * Base contract of every plugin. Plugins are discovered from the classpath
 * (META-INF/services) and must have a public no-arg constructor.
 */
public interface Plugin {
    /** Registry key: reportType, format or sinkId. Must be unique per plugin kind. */
    String key();

    default String version() { return "1.0.0"; }

    /** Called once after discovery. Read configuration here. */
    default void init(PluginContext context) { }

    /** Called on shutdown. Release resources here. */
    default void destroy() { }
}
