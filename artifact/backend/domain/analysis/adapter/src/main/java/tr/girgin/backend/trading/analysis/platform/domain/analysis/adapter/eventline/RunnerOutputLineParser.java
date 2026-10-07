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
import tools.jackson.databind.node.MissingNode;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.eventline.json.RunnerOutputLine;

/**
 * Parses runner output. Runner output is untrusted input: a line that is
 * not JSON or lacks the envelope yields nothing, and oversized text is cut down again.
 */
public final class RunnerOutputLineParser {

    private static final Set<String> ENVELOPE = Set.of("v", "ts", "run_id", "seq", "type");
    private static final int MAX_LINE = 2_000_000;
    private static final int MAX_TEXT = 200_000;

    private final JsonMapper json = JsonMapper.builder().build();

    public Optional<RunnerOutputLine> parse(String line) {
        return envelope(line).map(this::toLine);
    }

    /** The line as a JSON object with the event envelope, or nothing. */
    private Optional<JsonNode> envelope(String line) {
        if (line == null || line.isBlank() || line.length() > MAX_LINE) {
            return Optional.empty();
        }
        JsonNode node;
        try {
            node = json.readTree(line);
        } catch (JacksonException _) {
            return Optional.empty();
        }
        boolean valid = node.isObject()
                && node.path("seq").canConvertToLong()
                && node.path("type").isString();
        return valid ? Optional.of(node) : Optional.empty();
    }

    /** Type-specific fields are read from their own type's lines only; for any other type they are null. */
    private RunnerOutputLine toLine(JsonNode node) {
        String type = node.get("type").asString();
        JsonNode decision = type.equals("decision") ? node : MissingNode.getInstance();
        JsonNode finished = type.equals("run_finished") ? node : MissingNode.getInstance();
        JsonNode stats = type.equals("stats") ? node : MissingNode.getInstance();
        return new RunnerOutputLine(
                node.get("seq").asLong(),
                timestamp(node.path("ts").asString(null)),
                type,
                text(decision, "rating"),
                text(finished, "status"),
                text(finished, "error"),
                number(stats, "llm_calls"),
                number(stats, "tool_calls"),
                number(stats, "tokens_in"),
                number(stats, "tokens_out"),
                decimal(stats, "cost_usd"),
                decimal(stats, "elapsed_s"),
                payload(node));
    }

    private Map<String, Object> payload(JsonNode node) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> field : node.properties()) {
            if (!ENVELOPE.contains(field.getKey())) {
                payload.put(field.getKey(), limit(json.treeToValue(field.getValue(), Object.class)));
            }
        }
        return payload;
    }

    private static String timestamp(String value) {
        try {
            return Instant.parse(value).toString();
        } catch (DateTimeParseException | NullPointerException _) {
            return Instant.now().toString();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    /** A count: 0 when a line of its type lacks it, null on other types' lines. */
    private static Long number(JsonNode node, String field) {
        if (node.isMissingNode()) {
            return null;
        }
        JsonNode value = node.get(field);
        return value != null && value.canConvertToLong() ? value.asLong() : 0L;
    }

    private static Double decimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isNumber() ? value.asDouble() : null;
    }

    private static Object limit(Object value) {
        if (value instanceof String text && text.length() > MAX_TEXT) {
            return text.substring(0, MAX_TEXT) + "… [truncated]";
        }
        return value;
    }
}
