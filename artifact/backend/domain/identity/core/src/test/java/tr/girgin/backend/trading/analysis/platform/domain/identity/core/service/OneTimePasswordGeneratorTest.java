package tr.girgin.backend.trading.analysis.platform.domain.identity.core.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OneTimePasswordGeneratorTest {

    private final OneTimePasswordGenerator generator = new OneTimePasswordGenerator();

    @Test
    void generatesTwentyFourLettersAndDigits() {
        assertThat(generator.generate()).hasSize(24).matches("[A-Za-z0-9]+");
    }

    @Test
    void twoCallsDiffer() {
        assertThat(generator.generate()).isNotEqualTo(generator.generate());
    }
}
