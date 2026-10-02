package tr.girgin.backend.trading.analysis.platform.domain.catalog.adapter.runner;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.StreamType;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.adapter.runner.json.CatalogJson;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.adapter.runner.json.VersionJson;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.adapter.runner.mapper.CatalogJsonToCatalogMapper;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.adapter.runner.mapper.VersionJsonToEngineVersionMapper;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.adapter.runner.support.DockerClients;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.adapter.runner.support.RunnerKind;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.exception.CatalogUnavailableException;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.Catalog;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.EngineVersion;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.outbound.runner.EngineInfoPort;

/**
 * Runs {@code ta-runner catalog} / {@code version} in a short-lived container of the runner image,
 * locked down like an analysis container (and with no volume at all).
 */
@Component
@Conditional(RunnerKind.Docker.class)
class DockerEngineInfoAdapter implements EngineInfoPort, DisposableBean {

    private static final long TIMEOUT_SECONDS = 120;

    private final CatalogJsonToCatalogMapper catalogMapper;
    private final VersionJsonToEngineVersionMapper versionMapper;
    private final JsonMapper json = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    private final DockerClient docker;
    private final String image;

    @Autowired
    DockerEngineInfoAdapter(CatalogJsonToCatalogMapper catalogMapper,
                            VersionJsonToEngineVersionMapper versionMapper,
                            @Value("${platform.runner.docker.host}") String host,
                            @Value("${platform.runner.docker.image}") String image) {
        this(catalogMapper, versionMapper, DockerClients.create(host, Duration.ofMinutes(3)), image);
    }

    DockerEngineInfoAdapter(CatalogJsonToCatalogMapper catalogMapper,
                            VersionJsonToEngineVersionMapper versionMapper,
                            DockerClient docker,
                            String image) {
        this.catalogMapper = catalogMapper;
        this.versionMapper = versionMapper;
        this.docker = docker;
        this.image = image;
    }

    @Override
    public Catalog fetchCatalog() {
        return run("catalog", out -> catalogMapper.map(json.readValue(out, CatalogJson.class)));
    }

    @Override
    public EngineVersion fetchVersion() {
        return run("version", out -> versionMapper.map(json.readValue(out, VersionJson.class)));
    }

    @Override
    public void destroy() throws IOException {
        docker.close();
    }

    private <T> T run(String subcommand, Function<String, T> parse) {
        String containerId;
        try {
            containerId = docker.createContainerCmd(image)
                    .withCmd(subcommand)
                    .withLabels(Map.of("ta.platform.managed", "true", "ta.platform.command", subcommand))
                    .withHostConfig(HostConfig.newHostConfig()
                            .withReadonlyRootfs(true)
                            .withTmpFs(Map.of("/tmp", "rw,nosuid,nodev,size=64m"))
                            .withCapDrop(Capability.ALL)
                            .withSecurityOpts(List.of("no-new-privileges"))
                            .withMemory(512L * 1024 * 1024))
                    .exec()
                    .getId();
        } catch (NotFoundException e) {
            throw new CatalogUnavailableException("The ta-runner image " + image + " is not there: build or pull it", e);
        } catch (DockerException | IllegalArgumentException e) {
            throw new CatalogUnavailableException("Cannot start ta-runner (" + image + "): " + e.getMessage(), e);
        }
        try {
            docker.startContainerCmd(containerId).exec();
            Integer exit = docker.waitContainerCmd(containerId).start().awaitStatusCode(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (exit == null || exit != 0) {
                throw new CatalogUnavailableException("ta-runner " + subcommand + " exited with code " + exit, null);
            }
            return parse.apply(stdout(containerId));
        } catch (DockerException e) {
            throw new CatalogUnavailableException("ta-runner " + subcommand + " failed: " + e.getMessage(), e);
        } catch (JacksonException e) {
            throw new CatalogUnavailableException("Unreadable ta-runner " + subcommand + " output", e);
        } finally {
            try {
                docker.removeContainerCmd(containerId).withForce(true).exec();
            } catch (DockerException e) {
                // Best effort; the Docker runner removes leftovers too.
            }
        }
    }

    private String stdout(String containerId) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            docker.logContainerCmd(containerId).withStdOut(true).withStdErr(false)
                    .exec(new ResultCallback.Adapter<Frame>() {
                        @Override
                        public void onNext(Frame frame) {
                            if (frame.getStreamType() == StreamType.STDOUT || frame.getStreamType() == StreamType.RAW) {
                                out.writeBytes(frame.getPayload());
                            }
                        }
                    })
                    .awaitCompletion(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CatalogUnavailableException("Interrupted while reading ta-runner output", e);
        }
        return out.toString(StandardCharsets.UTF_8);
    }
}
