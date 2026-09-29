package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.eventline;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Parses runner output. Runner output is untrusted input (PLAN.md section 3.3): a line that is
 * not JSON or lacks the envelope yields nothing, and oversized text is cut down again.
 */
public final class RunnerOutputLineParser {

    private static final Set<String> ENVELOPE = Set.of("v", "ts", "run_id", "seq", "type");
    private static final int MAX_LINE = 2_000_000;
    private static final int MAX_TEXT = 200_000;

    private final JsonMapper json = JsonMapper.builder().build();

    public Optional<RunnerOutputLine> parse(String line) {
        if (line == null || line.isBlank() || line.length() > MAX_LINE) {
            return Optional.empty();
        }
        JsonNode node;
        try {
            node = json.readTree(line);
        } catch (JacksonException e) {
            return Optional.empty();
        }
        if (!node.isObject() || !node.path("seq").canConvertToLong() || !node.path("type").isString()) {
            return Optional.empty();
        }
        String type = node.get("type").asString();
        Map<String, Object> payload = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> field : node.properties()) {
            if (!ENVELOPE.contains(field.getKey())) {
                payload.put(field.getKey(), limit(json.treeToValue(field.getValue(), Object.class)));
            }
        }
        boolean stats = type.equals("stats");
        return Optional.of(new RunnerOutputLine(
                node.get("seq").asLong(),
                timestamp(node.path("ts").asString(null)),
                type,
                type.equals("decision") ? text(node, "rating") : null,
                type.equals("run_finished") ? text(node, "status") : null,
                type.equals("run_finished") ? text(node, "error") : null,
                stats ? number(node, "llm_calls") : null,
                stats ? number(node, "tool_calls") : null,
                stats ? number(node, "tokens_in") : null,
                stats ? number(node, "tokens_out") : null,
                stats && node.path("cost_usd").isNumber() ? node.get("cost_usd").asDouble() : null,
                stats && node.path("elapsed_s").isNumber() ? node.get("elapsed_s").asDouble() : null,
                payload));
    }

    private static String timestamp(String value) {
        try {
            return Instant.parse(value).toString();
        } catch (DateTimeParseException | NullPointerException e) {
            return Instant.now().toString();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static Long number(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.canConvertToLong() ? value.asLong() : 0L;
    }

    private static Object limit(Object value) {
        if (value instanceof String text && text.length() > MAX_TEXT) {
            return text.substring(0, MAX_TEXT) + "… [truncated]";
        }
        return value;
    }
}
