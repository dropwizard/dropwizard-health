package io.dropwizard.health.conf.response;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.dropwizard.health.core.HealthStatusChecker;
import io.dropwizard.jackson.Discoverable;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.http.HttpServlet;

/**
 * A factory for building an {@link HttpServlet} instance used for responding to health check requests.
 *
 * @see DefaultHealthServletFactory
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", defaultImpl = DefaultHealthServletFactory.class)
public interface HealthServletFactory extends Discoverable {
    /**
     * Build a servlet for responding to health check requests (e.g. from load balancer).
     *
     * @param isHealthy a flag indicating whether the application is healthy or not
     * @return a {@link HttpServlet} that responds to health check requests
     * @deprecated use {@link #build(HealthStatusChecker)} instead.
     */
    @Deprecated
    HttpServlet build(final AtomicBoolean isHealthy);

    /**
     * Build a servlet for responding to health check requests (e.g. from load balancer).
     *
     * @param healthStatusChecker an interface that exposes the ability to check current status of health.
     * @return a {@link HttpServlet} that responds to health check requests
     */
    HttpServlet build(final HealthStatusChecker healthStatusChecker);
}
