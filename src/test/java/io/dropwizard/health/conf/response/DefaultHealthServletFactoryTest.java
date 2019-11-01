package io.dropwizard.health.conf.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import io.dropwizard.configuration.YamlConfigurationFactory;
import io.dropwizard.jackson.DiscoverableSubtypeResolver;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.jersey.validation.Validators;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpTester;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlet.ServletTester;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.http.HttpServlet;
import javax.validation.Validator;
import javax.ws.rs.core.MediaType;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

public class DefaultHealthServletFactoryTest {
    private static final String HEALTH_CHECK_URI = "/health-check";
    private static final String PLAIN_TEXT_UTF_8 = "text/plain;charset=UTF-8";
    private final ObjectMapper mapper = Jackson.newObjectMapper();
    private final Validator validator = Validators.newValidator();
    private final YamlConfigurationFactory<HealthServletFactory> configFactory =
            new YamlConfigurationFactory<>(HealthServletFactory.class, validator, mapper, "dw");
    private final HttpTester.Request request = new HttpTester.Request();

    private ServletTester servletTester;

    @Before
    public void setUp() throws Exception {
        servletTester = new ServletTester();

        request.setHeader(HttpHeader.HOST.asString(), "localhost");
        request.setURI(HEALTH_CHECK_URI);
        request.setMethod("GET");
    }

    @After
    public void tearDown() throws Exception {
        servletTester.stop();
    }

    @Test
    public void isDiscoverable() {
        // given
        DiscoverableSubtypeResolver resolver = new DiscoverableSubtypeResolver();

        // when
        List<Class<?>> subtypes = resolver.getDiscoveredSubtypes();

        // then
        assertThat(subtypes, hasItems(DefaultHealthServletFactory.class));
    }

    @Test
    public void testBuildHealthServlet() throws Exception {
        // given
        File yml = new File(Resources.getResource("yml/servlet-factory-caching.yml").toURI());
        AtomicBoolean healthy = new AtomicBoolean(true);

        // when
        HealthServletFactory factory = configFactory.build(yml);
        HttpServlet servlet = factory.build(healthy);
        servletTester.addServlet(new ServletHolder(servlet), HEALTH_CHECK_URI);
        servletTester.start();
        HttpTester.Response healthyResponse = executeRequest(request);
        healthy.set(false);
        HttpTester.Response unhealthyResponse = executeRequest(request);

        // then
        assertThat(healthyResponse.getStatus(), is(Response.SC_OK));
        assertThat(healthyResponse.get(HttpHeader.CONTENT_TYPE), is(MediaType.APPLICATION_JSON));
        assertThat(parseResponseBody(healthyResponse), is("healthy"));
        assertThat(unhealthyResponse.getStatus(), is(Response.SC_SERVICE_UNAVAILABLE));
        assertThat(unhealthyResponse.get(HttpHeader.CONTENT_TYPE), is(MediaType.APPLICATION_JSON));
        assertThat(parseResponseBody(unhealthyResponse), is("unhealthy"));
    }

    @Test
    public void testBuildHealthServletWithCacheControlDisabled() throws Exception {
        // given
        File yml = new File(Resources.getResource("yml/servlet-factory-caching-header-disabled.yml").toURI());
        AtomicBoolean healthy = new AtomicBoolean(true);

        // when
        HealthServletFactory factory = configFactory.build(yml);
        HttpServlet servlet = factory.build(healthy);
        servletTester.addServlet(new ServletHolder(servlet), HEALTH_CHECK_URI);
        servletTester.start();
        HttpTester.Response healthyResponse = executeRequest(request);
        healthy.set(false);
        HttpTester.Response unhealthyResponse = executeRequest(request);

        // then
        assertThat(healthyResponse.getStatus(), is(Response.SC_OK));
        assertThat(healthyResponse.get(HttpHeader.CONTENT_TYPE), is(MediaType.APPLICATION_JSON));
        assertThat(parseResponseBody(healthyResponse), is("healthy"));
        assertThat(healthyResponse.get(HttpHeader.CACHE_CONTROL), is(nullValue()));
        assertThat(unhealthyResponse.getStatus(), is(Response.SC_SERVICE_UNAVAILABLE));
        assertThat(unhealthyResponse.get(HttpHeader.CONTENT_TYPE), is(MediaType.APPLICATION_JSON));
        assertThat(parseResponseBody(unhealthyResponse), is("unhealthy"));
        assertThat(unhealthyResponse.get(HttpHeader.CACHE_CONTROL), is(nullValue()));
    }

    @Test
    public void testBuildHealthServletWithCustomResponses() throws Exception {
        // given
        File yml = new File(Resources.getResource("yml/servlet-factory-custom-responses.yml").toURI());
        AtomicBoolean healthy = new AtomicBoolean(true);

        // when
        HealthServletFactory factory = configFactory.build(yml);
        HttpServlet servlet = factory.build(healthy);
        servletTester.addServlet(new ServletHolder(servlet), HEALTH_CHECK_URI);
        servletTester.start();
        HttpTester.Response healthyResponse = executeRequest(request);
        healthy.set(false);
        HttpTester.Response unhealthyResponse = executeRequest(request);

        // then
        assertThat(healthyResponse.getStatus(), is(Response.SC_OK));
        assertThat(healthyResponse.get(HttpHeader.CONTENT_TYPE), is(PLAIN_TEXT_UTF_8));
        assertThat(healthyResponse.getContent(), is("HAPPY"));
        assertThat(unhealthyResponse.getStatus(), is(Response.SC_SERVICE_UNAVAILABLE));
        assertThat(unhealthyResponse.get(HttpHeader.CONTENT_TYPE), is(PLAIN_TEXT_UTF_8));
        assertThat(unhealthyResponse.getContent(), is("SAD"));
    }

    private HttpTester.Response executeRequest(HttpTester.Request request) throws Exception {
        return HttpTester.parseResponse(servletTester.getResponses(request.generate()));
    }

    private String parseResponseBody(HttpTester.Response response) throws IOException {
        JsonNode jsonBody = mapper.readValue(response.getContentBytes(), JsonNode.class);
        return jsonBody.get("status").asText();
    }
}
