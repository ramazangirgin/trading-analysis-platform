package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.runner.support;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import java.time.Duration;

/** A Docker Engine API client for {@code unix:///...} or {@code tcp://...} (Docker, Podman, a socket proxy). */
public final class DockerClients {

    private static final int MAX_CONNECTIONS = 32;
    private static final int HTTP_INTERNAL_SERVER_ERROR = 500;
    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(10);

    private DockerClients() {}

    /**
     * {@code responseTimeout} bounds how long a followed log stream may stay silent before it is
     * reopened; the runner adapter reconnects, so it only needs to catch a dead connection.
     */
    public static DockerClient create(String host, Duration responseTimeout) {
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(host)
                .build();
        ApacheDockerHttpClient http = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(MAX_CONNECTIONS)
                .connectionTimeout(CONNECTION_TIMEOUT)
                .responseTimeout(responseTimeout)
                .build();
        return DockerClientImpl.getInstance(config, http);
    }

    /** Docker answers a missing container with 404; Podman's compatible API with 500 "no such container". */
    public static boolean isNoSuchContainer(DockerException e) {
        return e instanceof NotFoundException
                || e.getHttpStatus() == HTTP_INTERNAL_SERVER_ERROR
                        && e.getMessage() != null
                        && e.getMessage().contains("no such container");
    }
}
