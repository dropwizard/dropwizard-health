package io.dropwizard.health.tcp;

import com.google.common.util.concurrent.Uninterruptibles;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class TcpHealthCheckTest {
    private ServerSocket serverSocket;
    private TcpHealthCheck tcpHealthCheck;

    @Before
    public void setUp() throws IOException {
        serverSocket = new ServerSocket(0);
        tcpHealthCheck = new TcpHealthCheck("127.0.0.1", serverSocket.getLocalPort());
    }

    @After
    public void tearDown() throws IOException {
        serverSocket.close();
    }

    @Test
    public void tcpHealthCheckShouldReturnHealthyIfCanConnect() throws IOException {
        final ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.submit(() -> serverSocket.accept());
        assertThat(tcpHealthCheck.check().isHealthy())
                .isTrue();
    }

    @Test(expected = ConnectException.class)
    public void tcpHealthCheckShouldReturnUnhealthyIfCannotConnect() throws IOException {
        serverSocket.close();
        assertThat(tcpHealthCheck.check().isHealthy())
                .isFalse();
    }

    @Test(expected = ConnectException.class)
    public void tcpHealthCheckShouldReturnUnhealthyIfCannotConnectWithinConfiguredTimeout() throws IOException {
        final int port = serverSocket.getLocalPort();
        serverSocket.setReuseAddress(true);
        serverSocket.close();

        serverSocket = new ServerSocket();
        final ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.submit(() -> {
            Uninterruptibles.sleepUninterruptibly(tcpHealthCheck.getConnectionTimeout().toMillis() * 3, TimeUnit.MILLISECONDS);
            serverSocket.bind(new InetSocketAddress("127.0.0.1", port));
            return true;
        });

        assertThat(tcpHealthCheck.check().isHealthy())
                .isFalse();
    }
}
