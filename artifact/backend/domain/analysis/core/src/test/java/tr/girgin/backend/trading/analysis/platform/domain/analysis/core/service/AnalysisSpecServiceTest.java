package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisError;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisException;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisSpec;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analyst;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AssetType;

class AnalysisSpecServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);

    private final AnalysisSpecService service = new AnalysisSpecService();

    private static AnalysisSpec spec(String ticker, LocalDate tradeDate, int rounds, String language) {
        return new AnalysisSpec(
                ticker,
                tradeDate,
                AssetType.STOCK,
                List.of(Analyst.MARKET),
                "deepseek",
                "deepseek-v4-flash",
                "deepseek-v4-flash",
                rounds,
                rounds,
                language,
                false);
    }

    @Test
    void normalizedUppercasesTheTickerAndLowercasesTheProvider() {
        AnalysisSpec spec = new AnalysisSpec(
                " nvda ",
                TODAY,
                AssetType.STOCK,
                List.of(Analyst.MARKET),
                " DeepSeek ",
                "Model-A",
                "Model-B",
                2,
                3,
                "English",
                true);

        AnalysisSpec normalized = service.normalized(spec);

        assertThat(normalized.ticker()).isEqualTo("NVDA");
        assertThat(normalized.llmProvider()).isEqualTo("deepseek");
        assertThat(normalized.deepThinkLlm()).isEqualTo("Model-A");
        assertThat(normalized.maxRiskDiscussRounds()).isEqualTo(3);
        assertThat(normalized.checkpointEnabled()).isTrue();
    }

    @Test
    void normalizedSortsAnalystsAndDropsDuplicates() {
        Analyst[] all = Analyst.values();
        AnalysisSpec spec = new AnalysisSpec(
                "NVDA",
                TODAY,
                AssetType.STOCK,
                List.of(all[all.length - 1], all[0], all[all.length - 1]),
                "deepseek",
                "m",
                "m",
                1,
                1,
                "English",
                false);

        assertThat(service.normalized(spec).analysts()).containsExactly(all[0], all[all.length - 1]);
    }

    @Test
    void aValidSpecPasses() {
        assertThatCode(() -> service.validate(spec("BRK.B", TODAY, 1, "English"), TODAY))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "A B", "../x", "AAAAAAAAAAAAAAAAAAAAA"})
    void anInvalidTickerIsRejected(String ticker) {
        assertInvalid(spec(ticker, TODAY, 1, "English"), "ticker");
    }

    @Test
    void aTradeDateInTheFutureOrBefore1990IsRejected() {
        assertInvalid(spec("NVDA", TODAY.plusDays(1), 1, "English"), "tradeDate");
        assertInvalid(spec("NVDA", LocalDate.of(1989, 12, 31), 1, "English"), "tradeDate");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 11})
    void roundsOutsideOneToTenAreRejected(int rounds) {
        assertInvalid(spec("NVDA", TODAY, rounds, "English"), "maxDebateRounds");
    }

    @Test
    void anInvalidLanguageIsRejected() {
        assertInvalid(spec("NVDA", TODAY, 1, "English; rm"), "outputLanguage");
    }

    private void assertInvalid(AnalysisSpec spec, String field) {
        assertThatThrownBy(() -> service.validate(spec, TODAY)).isInstanceOfSatisfying(AnalysisException.class, e -> {
            assertThat(e.error()).isEqualTo(AnalysisError.INVALID_SPEC);
            assertThat(e.getMessage()).isEqualTo("Invalid " + field);
        });
    }
}
