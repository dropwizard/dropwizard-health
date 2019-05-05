package io.dropwizard.health;

import io.dropwizard.health.conf.HealthConfiguration;
import io.dropwizard.health.core.HealthCheckBundle;
import io.dropwizard.health.core.HealthCheckManager;

import com.codahale.metrics.InstrumentedThreadFactory;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import io.dropwizard.Configuration;
import io.dropwizard.configuration.YamlConfigurationFactory;
import io.dropwizard.health.core.HealthCheckServlet;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.jersey.validation.Validators;
import io.dropwizard.jetty.setup.ServletEnvironment;
import io.dropwizard.lifecycle.setup.LifecycleEnvironment;
import io.dropwizard.lifecycle.setup.ScheduledExecutorServiceBuilder;
import io.dropwizard.setup.Environment;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.concurrent.Executors;

import javax.servlet.ServletRegistration;
import javax.validation.Valid;
import javax.validation.Validator;
import javax.validation.constraints.NotNull;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class HealthCheckBundleTest {
    private final ObjectMapper objectMapper = Jackson.newObjectMapper();
    private final Validator validator = Validators.newValidator();
    private final YamlConfigurationFactory<HealthConfiguration> configFactory =
            new YamlConfigurationFactory<>(HealthConfiguration.class, validator, objectMapper, "dw");
    private final ExampleConfiguration config = new ExampleConfiguration();
    private final Environment env = mock(Environment.class);
    private final LifecycleEnvironment lifecycle = mock(LifecycleEnvironment.class);
    private final HealthCheckRegistry healthChecks = mock(HealthCheckRegistry.class);
    private final ServletEnvironment servlets = mock(ServletEnvironment.class);

    private HealthCheckBundle<ExampleConfiguration> bundle;

    @Before
    public void setUp() throws Exception {
        when(env.healthChecks())
                .thenReturn(healthChecks);
        when(env.metrics())
                .thenReturn(new MetricRegistry());
        when(env.lifecycle())
                .thenReturn(lifecycle);
        when(env.servlets())
                .thenReturn(servlets);
        final File yml = new File(Resources.getResource("yml/health.yml").toURI());
        final HealthConfiguration healthConfiguration = configFactory.build(yml);
        config.setHealth(healthConfiguration);

        bundle = new HealthCheckBundle<ExampleConfiguration>() {
            @Override
            protected HealthConfiguration getHealthConfiguration(final ExampleConfiguration configuration) {
                return healthConfiguration;
            }
        };
    }

    @Test
    public void shouldSuccessfullyConfigureHealthCheckBundle() {
        final ScheduledExecutorServiceBuilder executorServiceBuilder = mock(ScheduledExecutorServiceBuilder.class);
        when(lifecycle.scheduledExecutorService(
                eq("health-check-scheduled-executor"),
                any(InstrumentedThreadFactory.class)))
                .thenReturn(executorServiceBuilder);
        when(executorServiceBuilder.threads(anyInt())).thenReturn(executorServiceBuilder);
        when(executorServiceBuilder.build()).thenReturn(Executors.newSingleThreadScheduledExecutor());

        final ServletRegistration.Dynamic servletRegistration = mock(ServletRegistration.Dynamic.class);
        when(servlets.addServlet(
                eq("health-check"),
                any(HealthCheckServlet.class)))
                .thenReturn(servletRegistration);

        when(servletRegistration.addMapping(any()))
                .thenReturn(ImmutableSet.of());

        bundle.run(config, env);

        verify(servletRegistration).addMapping(config.getHealth().getHealthCheckUrlPaths().toArray(new String[0]));

        verify(healthChecks).addListener(any(HealthCheckManager.class));
    }

    private static class ExampleConfiguration extends Configuration {
        @Valid
        @NotNull
        @JsonProperty
        private HealthConfiguration health;

        public HealthConfiguration getHealth() {
            return health;
        }

        public void setHealth(final HealthConfiguration health) {
            this.health = health;
        }
    }
}
