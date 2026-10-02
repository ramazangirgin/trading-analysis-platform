package tr.girgin.backend.trading.analysis.platform.domain.catalog.adapter.runner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
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
import tr.girgin.backend.trading.analysis.platform.domain.catalog.adapter.runner.support.RunnerKind;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.exception.CatalogUnavailableException;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.Catalog;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.EngineVersion;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.outbound.runner.EngineInfoPort;

/** Runs {@code ta-runner catalog} / {@code version} with the same command the process runner uses. */
@Component
@Conditional(RunnerKind.Process.class)
class TaRunnerEngineInfoAdapter implements EngineInfoPort {

    private static final long TIMEOUT_SECONDS = 120;
    private static final long OUTPUT_TIMEOUT_SECONDS = 10;

    private final CatalogJsonToCatalogMapper catalogMapper;
    private final VersionJsonToEngineVersionMapper versionMapper;
    private final JsonMapper json = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    private final List<String> command;
    private final Path workingDir;

    TaRunnerEngineInfoAdapter(
            CatalogJsonToCatalogMapper catalogMapper,
            VersionJsonToEngineVersionMapper versionMapper,
            @Value("${platform.runner.process.command}") String[] command,
            @Value("${platform.runner.process.working-dir}") Path workingDir) {
        this.catalogMapper = catalogMapper;
        this.versionMapper = versionMapper;
        this.command = absoluteExecutable(List.of(command));
        this.workingDir = workingDir.toAbsolutePath();
    }

    @Override
    public Catalog fetchCatalog() {
        return run("catalog", out -> catalogMapper.map(json.readValue(out, CatalogJson.class)));
    }

    @Override
    public EngineVersion fetchVersion() {
        return run("version", out -> versionMapper.map(json.readValue(out, VersionJson.class)));
    }

    private <T> T run(String subcommand, Function<String, T> parse) {
        List<String> args = new ArrayList<>(command);
        args.add(subcommand);
        Process process;
        try {
            process = new ProcessBuilder(args)
                    .directory(workingDir.toFile())
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException e) {
            throw new CatalogUnavailableException("Cannot start ta-runner: " + e.getMessage(), e);
        }
        try {
            CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> readAll(process.getInputStream()));
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new CatalogUnavailableException("ta-runner " + subcommand + " timed out", null);
            }
            if (process.exitValue() != 0) {
                throw new CatalogUnavailableException(
                        "ta-runner " + subcommand + " exited with code " + process.exitValue(), null);
            }
            return parse.apply(output.get(OUTPUT_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new CatalogUnavailableException("Interrupted while running ta-runner", e);
        } catch (ExecutionException | java.util.concurrent.TimeoutException | JacksonException e) {
            throw new CatalogUnavailableException("Unreadable ta-runner " + subcommand + " output", e);
        }
    }

    private static String readAll(InputStream in) {
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static List<String> absoluteExecutable(List<String> command) {
        List<String> resolved = new ArrayList<>(command);
        if (!resolved.isEmpty() && resolved.getFirst().contains("/")) {
            resolved.set(
                    0, Path.of(resolved.getFirst()).toAbsolutePath().normalize().toString());
        }
        return resolved;
    }
}
