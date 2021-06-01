package io.dropwizard.health.core;

public interface HealthStatusChecker {
    default boolean isHealthy() {
        return isHealthy(null);
    }

    boolean isHealthy(final String type);
}
