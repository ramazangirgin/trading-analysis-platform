package tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.AnalysisDefaults;
import tr.girgin.backend.trading.analysis.platform.library.persistence.JpaAdapterTest;

@JpaAdapterTest
@SpringJUnitConfig(JpaAnalysisDefaultsRepositoryAdapterTest.Config.class)
class JpaAnalysisDefaultsRepositoryAdapterTest {

    @Autowired
    private JpaAnalysisDefaultsRepositoryAdapter jpaAnalysisDefaultsRepositoryAdapter;

    @Autowired
    private AnalysisDefaultsJpaRepository analysisDefaultsJpaRepository;

    @BeforeEach
    void emptyTheTable() {
        analysisDefaultsJpaRepository.deleteAll();
    }

    @Test
    void findOnAnEmptyTableIsEmpty() {
        assertThat(jpaAnalysisDefaultsRepositoryAdapter.find()).isEmpty();
    }

    @Test
    void aSaveWithoutAVersionInsertsTheRowAtVersionZero() {
        AnalysisDefaults saved = jpaAnalysisDefaultsRepositoryAdapter.save(defaults("openai", 1, null));

        assertThat(saved).isEqualTo(defaults("openai", 1, 0L));
        assertThat(jpaAnalysisDefaultsRepositoryAdapter.find()).contains(saved);
    }

    @Test
    void aSaveAtTheStoredVersionUpdatesTheRowAndRaisesTheVersion() {
        AnalysisDefaults saved = jpaAnalysisDefaultsRepositoryAdapter.save(defaults("openai", 1, null));

        AnalysisDefaults updated = jpaAnalysisDefaultsRepositoryAdapter.save(defaults("deepseek", 3, saved.version()));

        assertThat(updated).isEqualTo(defaults("deepseek", 3, 1L));
        assertThat(jpaAnalysisDefaultsRepositoryAdapter.find()).contains(updated);
    }

    @Test
    void aStaleSaveFailsAndLeavesTheNewerRowUnchanged() {
        AnalysisDefaults saved = jpaAnalysisDefaultsRepositoryAdapter.save(defaults("openai", 1, null));
        AnalysisDefaults firstCopy = jpaAnalysisDefaultsRepositoryAdapter.find().orElseThrow();
        AnalysisDefaults staleCopy = jpaAnalysisDefaultsRepositoryAdapter.find().orElseThrow();
        AnalysisDefaults firstWrite =
                jpaAnalysisDefaultsRepositoryAdapter.save(defaults("first", 2, firstCopy.version()));

        assertThatThrownBy(() -> jpaAnalysisDefaultsRepositoryAdapter.save(defaults("second", 3, staleCopy.version())))
                .isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(saved.version()).isZero();
        assertThat(jpaAnalysisDefaultsRepositoryAdapter.find()).contains(firstWrite);
    }

    @Test
    void aSaveWithoutAVersionFailsWhileTheRowExistsAndChangesNothing() {
        AnalysisDefaults saved = jpaAnalysisDefaultsRepositoryAdapter.save(defaults("openai", 1, null));

        assertThatThrownBy(() -> jpaAnalysisDefaultsRepositoryAdapter.save(defaults("other", 5, null)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jpaAnalysisDefaultsRepositoryAdapter.find()).contains(saved);
    }

    private static AnalysisDefaults defaults(String provider, int rounds, Long version) {
        return new AnalysisDefaults(provider, provider + "-deep", provider + "-quick", rounds, rounds, version);
    }

    @Configuration
    @AutoConfigurationPackage
    @ComponentScan(basePackageClasses = JpaAnalysisDefaultsRepositoryAdapterTest.class)
    static class Config {}
}
