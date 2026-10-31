package tr.girgin.backend.trading.analysis.platform.domain.identity.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityError;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityException;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;

class PasswordPolicyServiceTest {

    private static final Username ALICE = new Username("alice");
    private static final String LIST = "# a comment\n\nPassword1234567\nqwertyuiop12\n";

    private final PasswordPolicyService policy = new PasswordPolicyService(12, LIST);

    @Test
    void anUncommonPasswordOfTheMinimumLengthPasses() {
        assertThatCode(() -> policy.check("x7Kq-m2Rz9aB", ALICE)).doesNotThrowAnyException();
        assertThatCode(() -> policy.check("a b c d e f ", ALICE)).doesNotThrowAnyException();
    }

    @Test
    void elevenCharactersAreTooShortAtTheDefaultMinimum() {
        assertThat(reasons("x7Kq-m2Rz9a")).containsExactly("too_short");
    }

    @Test
    void theLengthIsCountedInCodePoints() {
        assertThatCode(() -> policy.check("😀".repeat(12), ALICE)).doesNotThrowAnyException();
        assertThat(reasons("😀".repeat(11))).containsExactly("too_short");
        assertThatCode(() -> policy.check("ğüşiöçıĞÜŞİÖÇ", ALICE)).doesNotThrowAnyException();
    }

    @Test
    void theMaximumIsOneHundredTwentyEightCodePoints() {
        assertThatCode(() -> policy.check("😀".repeat(128), ALICE)).doesNotThrowAnyException();
        assertThat(reasons("😀".repeat(129))).containsExactly("too_long");
    }

    @Test
    void aCommonPasswordIsRejectedInAnyCase() {
        assertThat(reasons("password1234567")).containsExactly("common");
        assertThat(reasons("PASSWORD1234567")).containsExactly("common");
        assertThat(reasons("QwertyUiop12")).containsExactly("common");
    }

    @Test
    void theUsernameIsRejectedInAnyCase() {
        Username name = new Username("Alice.Smith-2026");

        assertThatThrownBy(() -> policy.check("alice.smith-2026", name))
                .isInstanceOfSatisfying(
                        IdentityException.class,
                        e -> assertThat(e.params().get("reasons")).isEqualTo(List.of("same_as_username")));
    }

    @Test
    void severalBrokenRulesComeInTheFixedOrderWithTheLengths() {
        Username name = new Username("password");
        PasswordPolicyService withShortCommon = new PasswordPolicyService(12, "password\n");

        assertThatThrownBy(() -> withShortCommon.check("Password", name))
                .isInstanceOfSatisfying(IdentityException.class, e -> {
                    assertThat(e.error()).isEqualTo(IdentityError.WEAK_PASSWORD);
                    assertThat(e.params())
                            .containsEntry("reasons", List.of("too_short", "common", "same_as_username"))
                            .containsEntry("minLength", 12)
                            .containsEntry("maxLength", 128);
                    assertThat(e.getMessage())
                            .isEqualTo("The password breaks the password policy: too_short, common, same_as_username");
                });
    }

    @Test
    void theMessageNeverContainsThePassword() {
        assertThatThrownBy(() -> policy.check("Xy7-weak", ALICE))
                .isInstanceOfSatisfying(
                        IdentityException.class, e -> assertThat(e.getMessage()).doesNotContain("Xy7-weak"));
    }

    @Test
    void theConfiguredMinimumMustBeBetweenEightAndSixtyFour() {
        assertThatThrownBy(() -> new PasswordPolicyService(7, LIST))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("platform.password.min-length")
                .hasMessageContaining("8")
                .hasMessageContaining("64");
        assertThatThrownBy(() -> new PasswordPolicyService(65, LIST)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new PasswordPolicyService(8, LIST).minLength()).isEqualTo(8);
        assertThat(new PasswordPolicyService(64, LIST).minLength()).isEqualTo(64);
    }

    @Test
    void aLoweredMinimumLetsAnEightCharacterPasswordThrough() {
        PasswordPolicyService lowered = new PasswordPolicyService(8, LIST);

        assertThatCode(() -> lowered.check("x7Kq-m2R", ALICE)).doesNotThrowAnyException();
        assertThatThrownBy(() -> lowered.check("x7Kq-m2", ALICE)).isInstanceOf(IdentityException.class);
    }

    @Test
    void theBundledListLoadsAndHoldsTheUsualSuspects() {
        PasswordPolicyService bundled = new PasswordPolicyService(8);

        assertThat(bundled.minLength()).isEqualTo(8);
        assertThatThrownBy(() -> bundled.check("password", ALICE))
                .isInstanceOfSatisfying(
                        IdentityException.class,
                        e -> assertThat(e.params().get("reasons")).asList().contains("common"));
        assertThatThrownBy(() -> bundled.check("123456", ALICE))
                .isInstanceOfSatisfying(
                        IdentityException.class,
                        e -> assertThat(e.params().get("reasons")).asList().contains("common"));
    }

    @Test
    void anEmptyListFailsTheStartup() {
        assertThatThrownBy(() -> new PasswordPolicyService(12, "# only a header\n\n"))
                .isInstanceOf(IllegalStateException.class);
    }

    private List<String> reasons(String password) {
        try {
            policy.check(password, ALICE);
        } catch (IdentityException e) {
            @SuppressWarnings("unchecked")
            List<String> reasons = (List<String>) e.params().get("reasons");
            return reasons;
        }
        throw new AssertionError("The password " + password.length() + " characters long was accepted");
    }
}
