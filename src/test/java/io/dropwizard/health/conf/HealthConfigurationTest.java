package io.dropwizard.health.conf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import io.dropwizard.configuration.YamlConfigurationFactory;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.jersey.validation.Validators;
import org.junit.Test;

import java.io.File;
import java.util.stream.Collectors;

import javax.validation.Validator;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class HealthConfigurationTest {
    private final ObjectMapper objectMapper = Jackson.newObjectMapper();
    private final Validator validator = Validators.newValidator();
    private final YamlConfigurationFactory<HealthConfiguration> configFactory =
            new YamlConfigurationFactory<>(HealthConfiguration.class, validator, objectMapper, "dw");

    @Test
    public void shouldBuildHealthConfigurationFromYaml() throws Exception {
        final File yml = new File(Resources.getResource("yml/health.yml").toURI());
        final HealthConfiguration healthConfig = configFactory.build(yml);

        assertThat(healthConfig.isDelayedShutdownHandlerEnabled(), is(true));
        assertThat(healthConfig.getShutdownWaitPeriod().toMilliseconds(), is(1L));
        assertThat(healthConfig.getHealthCheckUrlPaths(), is(ImmutableList.of("/health-check")));

        assertThat(healthConfig.getHealthCheckConfigurations()
                        .stream()
                        .map(HealthCheckConfiguration::getName)
                        .collect(Collectors.toList()),
                hasItems("foundationdb", "kafka", "redis"));
        assertThat(healthConfig.getHealthCheckConfigurations()
                        .stream()
                        .map(HealthCheckConfiguration::isCritical)
                        .collect(Collectors.toList()),
                hasItems(true, false, false));
        healthConfig.getHealthCheckConfigurations().forEach(healthCheckConfig -> {
            assertThat(healthCheckConfig.getSchedule().getCheckInterval().toSeconds(), is(5L));
            assertThat(healthCheckConfig.getSchedule().getDowntimeInterval().toSeconds(), is(30L));
            assertThat(healthCheckConfig.getSchedule().getFailureAttempts(), is(3));
            assertThat(healthCheckConfig.getSchedule().getSuccessAttempts(), is(2));
        });
    }
}
