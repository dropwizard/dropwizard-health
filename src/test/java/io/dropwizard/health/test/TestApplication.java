package io.dropwizard.health.test;

import io.dropwizard.health.core.HealthCheckBundle;
import io.dropwizard.health.conf.HealthConfiguration;

import com.codahale.metrics.health.HealthCheck;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class TestApplication extends Application<TestApplication.TestConfiguration> {

    private final AtomicBoolean criticalCheckHealthy1 = new AtomicBoolean();
    private final AtomicBoolean criticalCheckHealthy2 = new AtomicBoolean();
    private final AtomicBoolean nonCriticalCheckHealthy = new AtomicBoolean();

    @Override
    public void initialize(Bootstrap<TestConfiguration> bootstrap) {
        bootstrap.addBundle(new HealthCheckBundle<TestConfiguration>() {
            @Override
            protected HealthConfiguration getHealthConfiguration(final TestConfiguration configuration) {
                return configuration.getHealth();
            }
        });
    }

    @Override
    public void run(final TestConfiguration testConfiguration, final Environment environment) {
        environment.healthChecks().register(HealthCheckIT.CRITICAL_HEALTH_CHECK_NAME_1, new HealthCheck() {
            @Override
            protected Result check() {
                return criticalCheckHealthy1.get() ? Result.healthy() : Result.builder().unhealthy().build();
            }
        });

        environment.healthChecks().register(HealthCheckIT.CRITICAL_HEALTH_CHECK_NAME_2, new HealthCheck() {
            @Override
            protected Result check() {

                return criticalCheckHealthy2.get() ? Result.healthy() : Result.builder().unhealthy().build();
            }
        });

        environment.healthChecks().register(HealthCheckIT.NON_CRITICAL_HEALTH_CHECK_NAME, new HealthCheck() {
            @Override
            protected Result check() throws Exception {
                return nonCriticalCheckHealthy.get() ? Result.healthy() : Result.builder().unhealthy().build();
            }
        });
    }

    AtomicBoolean getCriticalCheckHealthy1() {
        return criticalCheckHealthy1;
    }

    AtomicBoolean getCriticalCheckHealthy2() {
        return criticalCheckHealthy2;
    }

    AtomicBoolean getNonCriticalCheckHealthy() {
        return nonCriticalCheckHealthy;
    }

    public static class TestConfiguration extends Configuration {
        @Valid
        @NotNull
        private HealthConfiguration health;

        public HealthConfiguration getHealth() {
            return health;
        }

        public void setHealth(final HealthConfiguration health) {
            this.health = health;
        }
    }
}
