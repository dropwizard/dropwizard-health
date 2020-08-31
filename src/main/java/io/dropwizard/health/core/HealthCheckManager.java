package io.dropwizard.health.core;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheckRegistryListener;
import io.dropwizard.health.conf.HealthCheckConfiguration;
import io.dropwizard.health.conf.Schedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

public class HealthCheckManager implements HealthCheckRegistryListener, StateChangedCallback {
    private static final Logger log = LoggerFactory.getLogger(HealthCheckManager.class);

    private final AtomicBoolean isAppHealthy = new AtomicBoolean(false);
    private final AtomicInteger unhealthyCriticalHealthChecks = new AtomicInteger();
    private final HealthCheckScheduler scheduler;
    private final Map<String, ScheduledHealthCheck> checks;
    private final Map<String, HealthCheckConfiguration> configs;
    private final MetricRegistry metrics;
    private final String managerName;
    private final String aggregateHealthyName;
    private final String aggregateUnhealthyName;

    public HealthCheckManager(final List<HealthCheckConfiguration> configs,
                              final HealthCheckScheduler scheduler,
                              final MetricRegistry metrics) {
        this(configs, scheduler, metrics, null);
    }

    public HealthCheckManager(final List<HealthCheckConfiguration> configs,
                              final HealthCheckScheduler scheduler,
                              final MetricRegistry metrics,
                              final String managerName) {
        this(configs, scheduler, metrics, managerName, new HashMap<>());
    }

    // Visible for testing
    HealthCheckManager(final List<HealthCheckConfiguration> configs,
                       final HealthCheckScheduler scheduler,
                       final MetricRegistry metrics,
                       final String managerName,
                       final Map<String, ScheduledHealthCheck> checks) {
        this.configs = configs.stream()
                .collect(Collectors.toMap(HealthCheckConfiguration::getName, Function.identity()));
        this.scheduler = Objects.requireNonNull(scheduler);
        this.metrics = Objects.requireNonNull(metrics);
        this.managerName = managerName;
        this.checks = Objects.requireNonNull(checks);

        this.aggregateHealthyName = MetricRegistry.name("health", managerName, "aggregate", "healthy");
        this.aggregateUnhealthyName = MetricRegistry.name("health", managerName, "aggregate", "unhealthy");
        metrics.register(aggregateHealthyName, (Gauge) this::calculateNumberOfHealthyChecks);
        metrics.register(aggregateUnhealthyName, (Gauge) this::calculateNumberOfUnhealthyChecks);
    }

    @Override
    public void onHealthCheckAdded(final String name, final HealthCheck healthCheck) {
        final HealthCheckConfiguration config = configs.get(name);

        if (config == null) {
            log.debug("ignoring registered health check that isn't configured: name={}", name);
            return;
        }

        final Schedule schedule = config.getSchedule();
        final boolean critical = config.isCritical();

        final State state = new State(name, schedule.getFailureAttempts(), schedule.getSuccessAttempts(), this);
        final Counter healthyCheckCounter = metrics.counter(MetricRegistry.name("health", managerName, name, "healthy"));
        final Counter unhealthyCheckCounter = metrics.counter(MetricRegistry.name("health", managerName, name, "unhealthy"));

        final ScheduledHealthCheck check = new ScheduledHealthCheck(name, critical, healthCheck, schedule, state, healthyCheckCounter,
                unhealthyCheckCounter);
        checks.put(name, check);

        scheduler.schedule(check, true);
    }

    @Override
    public void onHealthCheckRemoved(final String name, final HealthCheck healthCheck) {
        scheduler.unschedule(name);
    }

    @Override
    public void onStateChanged(final String name, final boolean isNowHealthy) {
        log.debug("health check changed state: name={} state={}", name, isNowHealthy);
        final ScheduledHealthCheck check = checks.get(name);

        if (check == null) {
            log.error("State changed for unconfigured health check: name={} state={}", name, isNowHealthy);
            return;
        }

        if (check.isCritical()) {
            handleCriticalHealthChange(check.getName(), isNowHealthy);
        } else {
            handleNonCriticalHealthChange(check.getName(), isNowHealthy);
        }

        scheduler.schedule(check, isNowHealthy);
    }

    protected void initializeAppHealth() {
        this.isAppHealthy.set(true);
    }

    private long calculateNumberOfHealthyChecks() {
        return checks.values()
                .stream()
                .filter(ScheduledHealthCheck::isHealthy)
                .count();
    }

    private long calculateNumberOfUnhealthyChecks() {
        return checks.values()
                .stream()
                .filter(check -> !check.isHealthy())
                .count();
    }

    private void handleCriticalHealthChange(final String name, final boolean isNowHealthy) {
        final int newNumberOfUnhealthyCriticalHealthChecks;

        if (isNowHealthy) {
            log.info("A critical dependency is now healthy: name={}", name);
            newNumberOfUnhealthyCriticalHealthChecks = unhealthyCriticalHealthChecks.decrementAndGet();
        } else {
            log.error("A critical dependency is now unhealthy: name={}", name);
            newNumberOfUnhealthyCriticalHealthChecks = unhealthyCriticalHealthChecks.incrementAndGet();
        }

        log.debug("current status: unhealthy-critical={}", newNumberOfUnhealthyCriticalHealthChecks);

        isAppHealthy.set(newNumberOfUnhealthyCriticalHealthChecks == 0);
    }

    private void handleNonCriticalHealthChange(final String name, final boolean isNowHealthy) {
        if (isNowHealthy) {
            log.info("A non-critical dependency is now healthy: name={}", name);
        } else {
            log.warn("A non-critical dependency is now unhealthy: name={}", name);
        }
    }

    public AtomicBoolean getIsAppHealthy() {
        return isAppHealthy;
    }

    String getAggregateHealthyName() {
        return aggregateHealthyName;
    }

    String getAggregateUnhealthyName() {
        return aggregateUnhealthyName;
    }
}
