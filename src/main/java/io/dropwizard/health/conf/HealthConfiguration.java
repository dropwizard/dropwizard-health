package io.dropwizard.health.conf;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import io.dropwizard.util.Duration;
import org.hibernate.validator.constraints.NotEmpty;

import java.util.Collections;
import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class HealthConfiguration {

    @Valid
    @NotNull
    @JsonProperty
    private List<HealthCheckConfiguration> healthChecks = Collections.emptyList();

    @JsonProperty
    private boolean delayedShutdownHandlerEnabled = true;

    @NotNull
    @JsonProperty
    private Duration shutdownWaitPeriod = Duration.seconds(15);

    @NotEmpty
    @JsonProperty
    private List<String> healthCheckUrlPaths = ImmutableList.of("/health-check");

    public List<HealthCheckConfiguration> getHealthCheckConfigurations() {
        return healthChecks;
    }

    public void setHealthCheckConfigurations(final List<HealthCheckConfiguration> healthChecks) {
        this.healthChecks = healthChecks;
    }

    public boolean isDelayedShutdownHandlerEnabled() {
        return delayedShutdownHandlerEnabled;
    }

    public void setDelayedShutdownHandlerEnabled(final boolean delayedShutdownHandlerEnabled) {
        this.delayedShutdownHandlerEnabled = delayedShutdownHandlerEnabled;
    }

    public Duration getShutdownWaitPeriod() {
        return shutdownWaitPeriod;
    }

    public void setShutdownWaitPeriod(final Duration shutdownWaitPeriod) {
        this.shutdownWaitPeriod = shutdownWaitPeriod;
    }

    public List<String> getHealthCheckUrlPaths() {
        return healthCheckUrlPaths;
    }

    public void setHealthCheckUrlPaths(final List<String> healthCheckUrlPaths) {
        this.healthCheckUrlPaths = healthCheckUrlPaths;
    }
}
