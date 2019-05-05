package io.dropwizard.health.conf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import io.dropwizard.configuration.YamlConfigurationFactory;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.jersey.validation.Validators;
import org.junit.Test;

import java.io.File;

import javax.validation.Validator;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class HealthCheckConfigurationTest {
    private final ObjectMapper objectMapper = Jackson.newObjectMapper();
    private final Validator validator = Validators.newValidator();
    private final YamlConfigurationFactory<HealthCheckConfiguration> configFactory =
            new YamlConfigurationFactory<>(HealthCheckConfiguration.class, validator, objectMapper, "dw");

    @Test
    public void shouldBuildHealthCheckConfigurationFromYaml() throws Exception {
        final File yml = new File(Resources.getResource("yml/healthCheck.yml").toURI());
        final HealthCheckConfiguration healthCheckConfig = configFactory.build(yml);

        assertThat(healthCheckConfig.getName(), is("cassandra"));
        assertThat(healthCheckConfig.isCritical(), is(true));
        assertThat(healthCheckConfig.getSchedule().getCheckInterval().toSeconds(), is(5L));
        assertThat(healthCheckConfig.getSchedule().getDowntimeInterval().toSeconds(), is(30L));
        assertThat(healthCheckConfig.getSchedule().getFailureAttempts(), is(3));
        assertThat(healthCheckConfig.getSchedule().getSuccessAttempts(), is(2));
    }
}
