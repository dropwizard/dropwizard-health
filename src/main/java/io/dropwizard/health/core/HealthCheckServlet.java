package io.dropwizard.health.core;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.MediaType;

public class HealthCheckServlet extends HttpServlet {
    private static final String STATUS_TEMPLATE = "{\"status\": %s}";
    private static final String HEALTHY_STATUS = String.format(STATUS_TEMPLATE, "healthy");
    private static final String UNHEALTHY_STATUS = String.format(STATUS_TEMPLATE, "unhealthy");

    private final AtomicBoolean healthy;

    public HealthCheckServlet(final AtomicBoolean healthy) {
        this.healthy = healthy;
    }

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType(MediaType.APPLICATION_JSON);
        final PrintWriter writer = resp.getWriter();
        if (healthy.get()) {
            writer.print(HEALTHY_STATUS);
        } else {
            writer.print(UNHEALTHY_STATUS);
            resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }
    }
}
