package io.dropwizard.health.core;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.HttpHeaders;

import static java.util.Objects.requireNonNull;

public class HealthCheckServlet extends HttpServlet {
    private final AtomicBoolean healthy;
    private final boolean cacheControlEnabled;
    private final String cacheControlValue;
    private final String contentType;
    private final String healthyValue;
    private final String unhealthyValue;

    public HealthCheckServlet(final AtomicBoolean healthy,
                              final boolean cacheControlEnabled, final String cacheControlValue,
                              final String contentType, final String healthyValue, final String unhealthyValue) {
        this.healthy = requireNonNull(healthy);
        this.cacheControlEnabled = cacheControlEnabled;
        this.cacheControlValue = requireNonNull(cacheControlValue);
        this.contentType = requireNonNull(contentType);
        this.healthyValue = requireNonNull(healthyValue);
        this.unhealthyValue = requireNonNull(unhealthyValue);
    }

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        if (cacheControlEnabled) {
            resp.setHeader(HttpHeaders.CACHE_CONTROL, cacheControlValue);
        }

        resp.setContentType(contentType);
        final PrintWriter writer = resp.getWriter();
        if (healthy.get()) {
            writer.print(healthyValue);
        } else {
            writer.print(unhealthyValue);
            resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }
    }
}
