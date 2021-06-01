package io.dropwizard.health.shutdown;

import io.dropwizard.util.Duration;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.thread.ShutdownThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handler that sets {@code healthy} flag to {@code false} and delays shutdown to allow for a load balancer to
 * determine the instance should no longer receive requests.
 */
public class DelayedShutdownHandler extends AbstractLifeCycle {
    private static final Logger log = LoggerFactory.getLogger(DelayedShutdownHandler.class);
    private final ShutdownNotifier shutdownNotifier;

    @Deprecated
    public DelayedShutdownHandler(AtomicBoolean healthy, Duration shutdownWaitPeriod) {
        this(new LegacyShutdownNotifier(healthy, shutdownWaitPeriod));
    }

    public DelayedShutdownHandler(final ShutdownNotifier shutdownNotifier) {
        this.shutdownNotifier = shutdownNotifier;
    }

    public void register() {
        try {
            start(); // lifecycle must be started in order for stop() to be called

            // register the shutdown handler as first (index 0) so that it executes before Jetty's shutdown behavior
            ShutdownThread.register(0, this);
        } catch (Exception e) {
            log.error("failed setting up delayed shutdown handler", e);
            throw new IllegalStateException("failed setting up delayed shutdown handler", e);
        }
    }

    @Override
    protected void doStop() throws Exception {
        shutdownNotifier.notifyShutdownStarted();
    }

    private static class LegacyShutdownNotifier implements ShutdownNotifier {
        private final AtomicBoolean healthy;
        private final Duration shutdownWaitPeriod;

        public LegacyShutdownNotifier(AtomicBoolean healthy, Duration shutdownWaitPeriod) {
            this.healthy = healthy;
            this.shutdownWaitPeriod = shutdownWaitPeriod;
        }

        @Override
        public void notifyShutdownStarted() throws InterruptedException {
            log.info("delayed shutdown: started (waiting {})", shutdownWaitPeriod);

            // set healthy to false to indicate to the load balancer that it should not be in rotation for requests
            healthy.set(false);

            // sleep for period of time to give time for load balancer to realize requests should not be sent anymore
            Thread.sleep(shutdownWaitPeriod.toMilliseconds());

            log.info("delayed shutdown: finished");
        }
    }
}
