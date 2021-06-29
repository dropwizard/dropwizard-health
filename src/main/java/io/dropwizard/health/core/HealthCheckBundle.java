package io.dropwizard.health.core;

import com.codahale.metrics.InstrumentedScheduledExecutorService;
import com.codahale.metrics.InstrumentedThreadFactory;
import com.codahale.metrics.MetricRegistry;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.dropwizard.Configuration;
import io.dropwizard.ConfiguredBundle;
import io.dropwizard.health.conf.HealthCheckConfiguration;
import io.dropwizard.health.conf.HealthConfiguration;
import io.dropwizard.health.shutdown.DelayedShutdownHandler;
import io.dropwizard.lifecycle.setup.LifecycleEnvironment;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.dropwizard.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.http.HttpServlet;

public abstract class HealthCheckBundle<C extends Configuration> implements ConfiguredBundle<C> {
    private static final Logger log = LoggerFactory.getLogger(HealthCheckBundle.class);
    private static final String DEFAULT_BASE_NAME = "health-check";
    private final String baseName;
    private final String name;

    public HealthCheckBundle() {
        this(null);
    }

    protected HealthCheckBundle(String name) {
        this.name = name;
        if (name != null) {
            this.baseName = DEFAULT_BASE_NAME + "-" + name;
        } else {
            this.baseName = DEFAULT_BASE_NAME;
        }
    }

    @Override
    public void initialize(final Bootstrap<?> bootstrap) {
        // do nothing
    }

    @Override
    public void run(final C configuration, final Environment environment) {
        final MetricRegistry metrics = environment.metrics();
        final HealthConfiguration healthConfig = getHealthConfiguration(configuration);
        final List<HealthCheckConfiguration> healthCheckConfigs = healthConfig.getHealthCheckConfigurations();

        // setup schedules for configured health checks
        final ScheduledExecutorService scheduledHealthCheckExecutor = createScheduledExecutorForHealthChecks(
                healthCheckConfigs.size(), metrics, environment.lifecycle());
        final HealthCheckScheduler scheduler = new HealthCheckScheduler(scheduledHealthCheckExecutor);
        final HealthCheckManager healthCheckManager = createHealthCheckManager(healthCheckConfigs, scheduler, metrics,
                name, healthConfig.getShutdownWaitPeriod(), healthConfig.isInitialOverallState());
        healthCheckManager.initializeAppHealth();

        // setup servlet to respond to health check requests
        final HttpServlet servlet;
        final HttpServlet userProvidedServlet = createHealthCheckServlet(healthCheckManager.getIsAppHealthy());
        if (userProvidedServlet != null) {
            servlet = userProvidedServlet;
        } else {
            servlet = healthConfig.getServletFactory().build(healthCheckManager);
        }
        environment.servlets()
                .addServlet(baseName + "-servlet", servlet)
                .addMapping(healthConfig.getHealthCheckUrlPaths().toArray(new String[0]));

        // register listener for HealthCheckRegistry and setup validator to ensure correct config
        environment.healthChecks().addListener(healthCheckManager);
        environment.lifecycle().manage(new HealthCheckConfigValidator(healthCheckConfigs, environment.healthChecks()));

        // register shutdown handler with Jetty
        final Duration shutdownWaitPeriod = healthConfig.getShutdownWaitPeriod();
        if (healthConfig.isDelayedShutdownHandlerEnabled() && shutdownWaitPeriod.toMilliseconds() > 0) {
            final DelayedShutdownHandler shutdownHandler = new DelayedShutdownHandler(healthCheckManager);
            shutdownHandler.register();
        }
    }

    private ScheduledExecutorService createScheduledExecutorForHealthChecks(final int numberOfScheduledHealthChecks,
                                                                            final MetricRegistry metrics,
                                                                            final LifecycleEnvironment lifecycle) {
        final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat(baseName + "-%d")
                .setDaemon(true)
                .setUncaughtExceptionHandler((t, e) -> log.error("Thread={} died due to uncaught exception", t, e))
                .build();

        final InstrumentedThreadFactory instrumentedThreadFactory =
                new InstrumentedThreadFactory(threadFactory, metrics);

        final ScheduledExecutorService scheduledExecutorService =
                lifecycle.scheduledExecutorService(baseName + "-scheduled-executor", instrumentedThreadFactory)
                        .threads(numberOfScheduledHealthChecks)
                        .build();

        return new InstrumentedScheduledExecutorService(scheduledExecutorService, metrics);
    }

    /**
     * Creates an {@link HttpServlet} to expose health check endpoint(s).
     *
     * By default this will return null to indicate that the servlet factory should be used. If different behavior
     * is desired, this class must be extended and an alternate implementation for this method should be provided
     * that returns a non-null {@link HttpServlet}.
     *
     * @param isHealthy A boolean flag representing app health.
     * @return A created {@link HttpServlet} to expose health check endpoint(s).
     */
    protected HttpServlet createHealthCheckServlet(final AtomicBoolean isHealthy) {
        return null;
    }

    /**
     * @deprecated use {@link #createHealthCheckManager(List, HealthCheckScheduler, MetricRegistry, String, Duration, boolean)} instead.
     */
    @Deprecated
    protected HealthCheckManager createHealthCheckManager(final List<HealthCheckConfiguration> healthCheckConfigs,
                                                          final HealthCheckScheduler scheduler,
                                                          final MetricRegistry metrics) {
        return createHealthCheckManager(healthCheckConfigs, scheduler, metrics, null);
    }

    /**
     * @deprecated use {@link #createHealthCheckManager(List, HealthCheckScheduler, MetricRegistry, String, Duration, boolean)} instead.
     */
    @Deprecated
    protected HealthCheckManager createHealthCheckManager(final List<HealthCheckConfiguration> healthCheckConfigs,
                                                          final HealthCheckScheduler scheduler,
                                                          final MetricRegistry metrics,
                                                          final String name) {
        return new HealthCheckManager(healthCheckConfigs, scheduler, metrics, name);
    }

    protected HealthCheckManager createHealthCheckManager(final List<HealthCheckConfiguration> healthCheckConfigs,
                                                          final HealthCheckScheduler scheduler,
                                                          final MetricRegistry metrics,
                                                          final String name,
                                                          final Duration shutdownWaitPeriod,
                                                          final boolean initialOverallState) {
        return new HealthCheckManager(healthCheckConfigs, scheduler, metrics, name, shutdownWaitPeriod,
                initialOverallState);
    }

    protected abstract HealthConfiguration getHealthConfiguration(C configuration);
}
