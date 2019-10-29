package io.dropwizard.health.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class HealthCheckScheduler {
    private static final Logger log = LoggerFactory.getLogger(HealthCheckScheduler.class);

    private final ScheduledExecutorService executorService;
    private final Map<String, ScheduledFuture> futures = new ConcurrentHashMap<>();

    public HealthCheckScheduler(final ScheduledExecutorService executorService) {
        this.executorService = executorService;
    }

    public void schedule(final ScheduledHealthCheck check, final boolean healthy) {
        unschedule(check.getName());

        final long intervalMs;
        if (healthy) {
            intervalMs = check.getSchedule().getCheckInterval().toMilliseconds();
        } else {
            intervalMs = check.getSchedule().getDowntimeInterval().toMilliseconds();
        }

        final ScheduledFuture taskFuture =
                executorService.scheduleWithFixedDelay(check, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        futures.put(check.getName(), taskFuture);
        log.debug("Scheduled check: check={}", check);
    }

    public void unschedule(final String name) {
        final ScheduledFuture taskFuture = futures.get(name);
        if (taskFuture != null) {
            taskFuture.cancel(true);
            futures.remove(name);
            log.debug("Unscheduled check: name={}", name);
        }
    }
}
