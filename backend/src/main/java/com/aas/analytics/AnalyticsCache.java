package com.aas.analytics;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Short-lived cache of dashboard results (ADR-03 in the SAD, kept in memory instead of Redis). Any change to an
 * employee, separation or survey answer clears it through {@link AnalyticsInvalidation}, so figures are never stale.
 */
@Component
public class AnalyticsCache {

    static final Duration TTL = Duration.ofMinutes(5);
    private static final AtomicLong VERSION = new AtomicLong();

    private record Entry(Object value, long version, long expiresAt) {
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    /** Called whenever data behind the figures changes. */
    public static void invalidateAll() {
        VERSION.incrementAndGet();
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Supplier<T> compute) {
        long version = VERSION.get();
        long now = System.currentTimeMillis();
        Entry hit = entries.get(key);
        if (hit != null && hit.version() == version && hit.expiresAt() > now) {
            return (T) hit.value();
        }
        T value = compute.get();
        if (entries.size() > 10_000) {
            entries.clear();
        }
        entries.put(key, new Entry(value, version, now + TTL.toMillis()));
        return value;
    }
}
