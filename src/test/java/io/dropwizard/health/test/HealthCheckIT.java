package io.dropwizard.health.test;

import com.google.common.primitives.Longs;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.DropwizardTestSupport;
import org.awaitility.Awaitility;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.Optional;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.Response;

import static org.assertj.core.api.Assertions.assertThat;

public class HealthCheckIT {
    static final String CRITICAL_HEALTH_CHECK_NAME_1 = "critical1";
    static final String CRITICAL_HEALTH_CHECK_NAME_2 = "critical2";
    static final String NON_CRITICAL_HEALTH_CHECK_NAME = "nonCritical";

    private static final String CONFIG_PATH = "src/test/resources/yml/config.yml";
    private static final String HOST = "localhost";
    private static final String APP_PORT_KEY = "server.connector.port";
    private static final String APP_PORT = "0";
    private static final String ENDPOINT = "/health-check";
    private static final String TEST_TIMEOUT_MS_OVERRIDE_ENV_VAR = "HEALTH_CHECK_TEST_TIMEOUT";
    private static final Duration APP_STARTUP_MAX_TIMEOUT = Duration.ofMinutes(1);
    private static final Duration POLL_DELAY = Duration.ofMillis(10);


    private static Duration testTimeout = Optional.ofNullable(System.getenv(TEST_TIMEOUT_MS_OVERRIDE_ENV_VAR))
            .map(Longs::tryParse)
            .map(Duration::ofMillis)
            // Default to 5 seconds
            .orElse(Duration.ofSeconds(5));

    private DropwizardTestSupport<TestApplication.TestConfiguration> rule;
    private Client client;
    private String hostUrl;

    @Before
    public void setUp() throws Exception {
        this.client = new JerseyClientBuilder().build();
        rule = new DropwizardTestSupport<>(TestApplication.class, CONFIG_PATH, ConfigOverride.config(APP_PORT_KEY, APP_PORT));
        rule.before();
        hostUrl = "http://" + HOST + ":" + rule.getLocalPort();
        Awaitility.waitAtMost(APP_STARTUP_MAX_TIMEOUT)
                .pollInSameThread()
                .pollDelay(POLL_DELAY)
                .until(this::isHealthCheckResponding);
    }

    @After
    public void tearDown() {
        this.client.close();
        rule.after();
    }

    @Test
    public void healthCheckShouldReportUnhealthyOnInitialStart() {
        assertThat(isAppHealthy()).isFalse();
    }

    @Test
    public void healthCheckShouldReportHealthyWhenInitialStateFalseCriticalCheckGoesHealthy() {
        final TestApplication app = rule.getApplication();

        assertThat(isAppHealthy()).isFalse();

        app.getCriticalCheckHealthy1().set(true);
        app.getCriticalCheckHealthy2().set(true);

        Awaitility.waitAtMost(testTimeout)
                .pollInSameThread()
                .pollDelay(POLL_DELAY)
                .until(this::isAppHealthy);
    }

    @Test
    public void healthCheckShouldReportHealthyWhenAllHealthChecksHealthy() {
        final TestApplication app = rule.getApplication();
        app.getCriticalCheckHealthy1().set(true);
        app.getCriticalCheckHealthy2().set(true);
        app.getNonCriticalCheckHealthy().set(true);

        Awaitility.await()
                .pollInSameThread()
                .atMost(testTimeout)
                .pollDelay(POLL_DELAY)
                .until(this::isAppHealthy);
    }

    @Test
    public void nonCriticalHealthCheckFailureShouldNotResultInUnhealthyApp() {
        final TestApplication app = rule.getApplication();
        app.getCriticalCheckHealthy1().set(true);
        app.getCriticalCheckHealthy2().set(true);
        app.getNonCriticalCheckHealthy().set(false);

        Awaitility.await()
                .pollInSameThread()
                .atMost(testTimeout)
                .pollDelay(POLL_DELAY)
                .until(this::isAppHealthy);
    }

    @Test
    public void criticalHealthCheckFailureShouldResultInUnhealthyApp() {
        final TestApplication app = rule.getApplication();
        app.getCriticalCheckHealthy1().set(false);

        Awaitility.waitAtMost(testTimeout)
                .pollInSameThread()
                .pollDelay(POLL_DELAY)
                .until(() -> !isAppHealthy());
    }

    @Test
    public void appShouldRecoverOnceCriticalCheckReturnsToHealthyStatus() {
        final TestApplication app = rule.getApplication();
        app.getCriticalCheckHealthy1().set(false);

        Awaitility.waitAtMost(testTimeout)
                .pollInSameThread()
                .pollDelay(POLL_DELAY)
                .until(() -> !isAppHealthy());

        app.getCriticalCheckHealthy1().set(true);
        app.getCriticalCheckHealthy2().set(true);

        Awaitility.waitAtMost(testTimeout)
                .pollInSameThread()
                .pollDelay(POLL_DELAY)
                .until(this::isAppHealthy);
    }

    private boolean isAppHealthy() {
        return client.target(hostUrl + ENDPOINT)
                .request()
                .get()
                .getStatus() == 200;
    }

    private boolean isHealthCheckResponding() {
        final Response response = client.target(hostUrl + ENDPOINT)
                .request()
                .get();
        return response.getStatus() == 200 || response.getStatus() == 503;
    }
}
