package com.vinabeef.reporting.plugins.nadcs;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.function.Predicate;

/** delay(n) = initialDelay * multiplier^(n-1). Retries on exceptions and on results the predicate marks retryable. */
class ExponentialBackoffRetry {

    record Outcome<T>(T value, Exception error, int attempts) {}

    private final int maxAttempts;
    private final Duration initialDelay;
    private final double multiplier;

    ExponentialBackoffRetry(int maxAttempts, Duration initialDelay, double multiplier) {
        this.maxAttempts = maxAttempts;
        this.initialDelay = initialDelay;
        this.multiplier = multiplier;
    }

    <T> Outcome<T> execute(Callable<T> task, Predicate<T> retryable) {
        T last = null;
        Exception lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                last = task.call();
                lastError = null;
                if (!retryable.test(last)) return new Outcome<>(last, null, attempt);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new Outcome<>(null, e, attempt);
            } catch (Exception e) {
                lastError = e;
            }
            if (attempt < maxAttempts) sleep(attempt);
        }
        return new Outcome<>(last, lastError, maxAttempts);
    }

    private void sleep(int attempt) {
        long millis = (long) (initialDelay.toMillis() * Math.pow(multiplier, attempt - 1));
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
