package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;

class AnalysisIdServiceTest {

    private final AnalysisIdService service = new AnalysisIdService();

    @ParameterizedTest
    @ValueSource(strings = {"r_1", "abc-DEF_09", "a"})
    void aSimpleNameIsValid(String value) {
        assertThat(service.isValid(value)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a b", "../etc", "a/b", "a.b", "ünï"})
    void anythingElseIsInvalid(String value) {
        assertThat(service.isValid(value)).isFalse();
    }

    @Test
    void sixtyFourCharactersAreValidAndSixtyFiveAreNot() {
        assertThat(service.isValid("a".repeat(64))).isTrue();
        assertThat(service.isValid("a".repeat(65))).isFalse();
    }

    @Test
    void newIdsAreValidAndDiffer() {
        AnalysisId first = service.newId();

        assertThat(first.value()).startsWith("r_");
        assertThat(service.isValid(first.value())).isTrue();
        assertThat(service.newId()).isNotEqualTo(first);
    }

    @Test
    void aValidIdPassesTheRequirement() {
        assertThatCode(() -> service.requireValid(new AnalysisId("r_1"))).doesNotThrowAnyException();
    }
}
