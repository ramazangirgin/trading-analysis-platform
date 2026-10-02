package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.eventline.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.AdapterTestSupport;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.eventline.RunnerOutputLineParser;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Rating;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunEvent;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunEventType;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunOutcome;

class RunnerOutputLineToRunEventMapperTest extends AdapterTestSupport {

    private final RunnerOutputLineParser parser = new RunnerOutputLineParser();

    @Autowired
    private RunnerOutputLineToRunEventMapper mapper;

    @Autowired
    private StringToRatingMapper ratingMapper;

    @Test
    void mapsTheEnvelopeAndKeepsThePayloadVerbatim() {
        RunEvent event = map("""
                {"v":1,"ts":"2026-09-28T10:00:01.123Z","run_id":"r_1","seq":17,"type":"agent_status",\
                "agent":"Market Analyst","status":"in_progress"}""");

        assertThat(event.seq()).isEqualTo(17);
        assertThat(event.timestamp()).isEqualTo(Instant.parse("2026-09-28T10:00:01.123Z"));
        assertThat(event.type()).isEqualTo(RunEventType.AGENT_STATUS);
        assertThat(event.payload()).containsExactly(
                org.assertj.core.api.Assertions.entry("agent", "Market Analyst"),
                org.assertj.core.api.Assertions.entry("status", "in_progress"));
        // "status" here is an agent status, not a run outcome.
        assertThat(event.outcome()).isNull();
        assertThat(event.stats()).isNull();
    }

    @Test
    void mapsStats() {
        RunEvent event = map("""
                {"v":1,"ts":"2026-09-28T10:00:01Z","run_id":"r_1","seq":5,"type":"stats","llm_calls":12,\
                "tool_calls":10,"tokens_in":77137,"tokens_out":48090,"cost_usd":0.42,"elapsed_s":260.5}""");

        assertThat(event.stats().llmCalls()).isEqualTo(12);
        assertThat(event.stats().tokensOut()).isEqualTo(48090);
        assertThat(event.stats().costUsd()).isEqualByComparingTo(new BigDecimal("0.42"));
        assertThat(event.stats().elapsed()).isEqualTo(Duration.ofMillis(260_500));
    }

    @Test
    void mapsDecisionAndRunEnd() {
        RunEvent decision = map("""
                {"v":1,"ts":"2026-09-28T10:00:01Z","run_id":"r_1","seq":6,"type":"decision","rating":"Overweight","raw":"x"}""");
        RunEvent failed = map("""
                {"v":1,"ts":"2026-09-28T10:00:01Z","run_id":"r_1","seq":7,"type":"run_finished","status":"error",\
                "error_type":"OpenAIAuthenticationError","error":"401"}""");

        assertThat(decision.rating()).isEqualTo(Rating.OVERWEIGHT);
        assertThat(failed.outcome()).isEqualTo(RunOutcome.FAILED);
        assertThat(failed.error()).isEqualTo("401");
        assertThat(failed.payload()).containsEntry("error_type", "OpenAIAuthenticationError");
    }

    @Test
    void unknownTypesBecomeLogEvents() {
        assertThat(map("""
                {"v":1,"ts":"2026-09-28T10:00:01Z","run_id":"r_1","seq":8,"type":"from_the_future"}""").type())
                .isEqualTo(RunEventType.LOG);
    }

    @Test
    void rejectsLinesThatAreNotProtocolEvents() {
        assertThat(parser.parse("Traceback (most recent call last):")).isEmpty();
        assertThat(parser.parse("[1,2,3]")).isEmpty();
        assertThat(parser.parse("{\"type\":\"log\"}")).isEmpty();
        assertThat(parser.parse("")).isEmpty();
    }

    @Test
    void replacesAnUnparseableTimestamp() {
        RunEvent event = map("""
                {"v":1,"ts":"yesterday","run_id":"r_1","seq":1,"type":"log"}""");

        assertThat(event.timestamp()).isBeforeOrEqualTo(Instant.now());
    }

    @ParameterizedTest
    @CsvSource({"Buy,BUY", "OVERWEIGHT,OVERWEIGHT", "hold.,HOLD", "**Sell**,SELL", "Under weight,UNDERWEIGHT",
            "REVIEW,REVIEW", "strong buy,REVIEW", "'',REVIEW"})
    void mapsRatingsLeniently(String raw, Rating expected) {
        assertThat(ratingMapper.map(raw)).isEqualTo(expected);
    }

    private RunEvent map(String line) {
        return parser.parse(line).map(mapper::map).orElseThrow();
    }
}
