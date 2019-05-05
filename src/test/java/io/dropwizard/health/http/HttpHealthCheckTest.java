package io.dropwizard.health.http;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.ws.rs.ProcessingException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

public class HttpHealthCheckTest {
    private static final String PATH = "/health-check";

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());

    private HttpHealthCheck httpHealthCheck;

    @Before
    public void setUp() {
        this.httpHealthCheck = new HttpHealthCheck(wireMockRule.url(PATH));
    }

    @Test
    public void httpHealthCheckShouldConsiderA200ResponseHealthy() {
        stubFor(get(urlEqualTo(PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("HAPPY")));

        assertThat(httpHealthCheck.check().isHealthy()).isTrue();
    }

    @Test
    public void httpHealthCheckShouldConsiderA500ResponseUnhealthy() {
        stubFor(get(urlEqualTo(PATH))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("SAD")));

        assertThat(httpHealthCheck.check().isHealthy()).isFalse();
    }

    @Test(expected = ProcessingException.class)
    public void httpHealthCheckShouldConsiderATimeoutUnhealthy() {
        stubFor(get(urlEqualTo(PATH))
                .willReturn(aResponse()
                        .withFixedDelay((int) (httpHealthCheck.DEFAULT_TIMEOUT.toMillis() * 2))
                        .withStatus(200)
                        .withBody("HAPPY")));

        assertThat(httpHealthCheck.check().isHealthy()).isFalse();
    }

    @Test
    public void httpHealthCheckShouldConsiderAFaultUnhealthyButThenRecover() {
        stubFor(get(urlEqualTo(PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("HAPPY")));

        assertThat(httpHealthCheck.check().isHealthy()).isTrue();

        stubFor(get(urlEqualTo(PATH))
                .willReturn(aResponse()
                        .withFault(Fault.RANDOM_DATA_THEN_CLOSE)
                        .withStatus(200)));

        try {
            httpHealthCheck.check();
            fail("Exception should have been thrown");
        } catch (final Exception e) {
            // swallow
        }

        stubFor(get(urlEqualTo(PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("HAPPY")));

        assertThat(httpHealthCheck.check().isHealthy()).isTrue();
    }
}
