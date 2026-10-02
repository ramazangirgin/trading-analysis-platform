package tr.girgin.backend.trading.analysis.platform.domain.catalog.adapter.runner.support;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import java.time.Duration;

/** A Docker Engine API client for {@code unix:///...} or {@code tcp://...} (Docker, Podman, a socket proxy). */
public final class DockerClients {

    private DockerClients() {
    }

    public static DockerClient create(String host, Duration responseTimeout) {
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
}
