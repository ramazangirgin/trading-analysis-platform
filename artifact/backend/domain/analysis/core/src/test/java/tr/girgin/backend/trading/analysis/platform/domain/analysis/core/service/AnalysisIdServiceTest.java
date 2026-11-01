package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisError;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisException;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;

class AnalysisIdServiceTest {

    private final AnalysisIdService analysisIdService = new AnalysisIdService();

    @ParameterizedTest
    @ValueSource(strings = {"r_1", "abc-DEF_09", "a"})
    void aSimpleNameIsValid(String value) {
        assertThat(analysisIdService.isValid(value)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a b", "../etc", "a/b", "a.b", "ünï"})
    void anythingElseIsInvalid(String value) {
        assertThat(analysisIdService.isValid(value)).isFalse();
    }

    @Test
    void sixtyFourCharactersAreValidAndSixtyFiveAreNot() {
        assertThat(analysisIdService.isValid("a".repeat(64))).isTrue();
        assertThat(analysisIdService.isValid("a".repeat(65))).isFalse();
    }

    @Test
    void newIdsAreValidAndDiffer() {
        AnalysisId first = analysisIdService.newId();

        assertThat(first.value()).startsWith("r_");
        assertThat(analysisIdService.isValid(first.value())).isTrue();
        assertThat(analysisIdService.newId()).isNotEqualTo(first);
    }

    @Test
    void aValidIdPassesTheRequirement() {
        assertThatCode(() -> analysisIdService.requireValid(new AnalysisId("r_1")))
                .doesNotThrowAnyException();
    }

    @Test
    void anInvalidIdIsNotFound() {
        assertThatThrownBy(() -> analysisIdService.requireValid(new AnalysisId("../etc")))
                .isInstanceOfSatisfying(AnalysisException.class, e -> {
                    assertThat(e.error()).isEqualTo(AnalysisError.NOT_FOUND);
                    assertThat(e.params()).containsEntry("id", "../etc");
                });
    }
}
