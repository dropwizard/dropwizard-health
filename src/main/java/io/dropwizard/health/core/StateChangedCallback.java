package io.dropwizard.health.core;

@FunctionalInterface
public interface StateChangedCallback {
    void onStateChanged(String healthCheckName, boolean healthy);
}
