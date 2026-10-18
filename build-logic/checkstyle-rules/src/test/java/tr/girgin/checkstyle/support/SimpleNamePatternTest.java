package tr.girgin.checkstyle.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SimpleNamePatternTest {

    @ParameterizedTest(name = "{0} on {1}: {2}")
    @CsvSource({
        "Exact, Exact, true",
        "Exact, ExactX, false",
        "Exact, XExact, false",
        "Prefix*, Prefix, true",
        "Prefix*, PrefixX, true",
        "Prefix*, XPrefix, false",
        "*Suffix, Suffix, true",
        "*Suffix, XSuffix, true",
        "*Suffix, SuffixX, false",
        "*Middle*, Middle, true",
        "*Middle*, XMiddleY, true",
        "*Middle*, Muddle, false",
        "*, Anything, true",
    })
    void matchesBySimpleName(String pattern, String name, boolean expected) {
        assertThat(SimpleNamePattern.parse(pattern).matches(name)).isEqualTo(expected);
    }
}
