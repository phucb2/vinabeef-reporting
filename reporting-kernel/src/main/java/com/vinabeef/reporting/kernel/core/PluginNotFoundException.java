package com.vinabeef.reporting.kernel.core;

/** Unknown reportType / format / sinkId. Mapped to HTTP 400 by the web layer. */
public class PluginNotFoundException extends RuntimeException {
    public PluginNotFoundException(String kind, String key, java.util.Set<String> available) {
        super("Unknown " + kind + " '" + key + "'. Available: " + available);
    }
}
