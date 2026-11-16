package com.vinabeef.reporting.kernel.spi;

import java.time.Clock;
import java.util.Optional;

/** What the kernel offers to plugins. Keeps plugins independent of Spring. */
public interface PluginContext {
    Optional<String> config(String key);

    default String requireConfig(String key) {
        return config(key).orElseThrow(() -> new IllegalStateException("Missing plugin config: " + key));
    }

    Clock clock();
}
