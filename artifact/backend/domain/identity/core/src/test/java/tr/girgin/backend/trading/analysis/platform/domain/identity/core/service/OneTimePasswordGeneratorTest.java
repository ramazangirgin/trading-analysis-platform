package tr.girgin.backend.trading.analysis.platform.domain.identity.core.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OneTimePasswordGeneratorTest {

    private final OneTimePasswordGenerator generator = new OneTimePasswordGenerator();

    @Test
    void generatesTheGivenLengthOfLettersAndDigits() {
        assertThat(generator.generate(24)).hasSize(24).matches("[A-Za-z0-9]+");
        assertThat(generator.generate(40)).hasSize(40).matches("[A-Za-z0-9]+");
    }

    @Test
    void twoCallsDiffer() {
        assertThat(generator.generate(24)).isNotEqualTo(generator.generate(24));
    }
}
