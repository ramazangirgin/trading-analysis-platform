package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.runner;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import java.time.Duration;

/** A Docker Engine API client for {@code unix:///...} or {@code tcp://...} (Docker, Podman, a socket proxy). */
final class DockerClients {

    private DockerClients() {
    }

    /**
     * {@code responseTimeout} bounds how long a followed log stream may stay silent before it is
     * reopened; the runner adapter reconnects, so it only needs to catch a dead connection.
     */
    static DockerClient create(String host, Duration responseTimeout) {
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(host)
                .build();
        ApacheDockerHttpClient http = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(32)
                .connectionTimeout(Duration.ofSeconds(10))
                .responseTimeout(responseTimeout)
                .build();
        return DockerClientImpl.getInstance(config, http);
    }

    /** Docker answers a missing container with 404; Podman's compatible API with 500 "no such container". */
    static boolean isNoSuchContainer(DockerException e) {
        return e instanceof NotFoundException
                || e.getHttpStatus() == 500 && e.getMessage() != null && e.getMessage().contains("no such container");
    }
}
