package tr.girgin.backend.trading.analysis.platform.library.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Proves {@link EntitySchemaRule} reports each way an entity can leave its schema, and none for a correct one. */
class EntitySchemaRuleTest {

    private static final String FIXTURES =
            "tr.girgin.backend.trading.analysis.platform.library.persistence.fixture.entities";

    @Test
    void reportsExactlyTheViolationsOfTheBrokenEntities() {
        JavaClasses classes = new ClassFileImporter().importPackages(FIXTURES);

        assertEquals(
                List.of(
                        "CollectionWithoutSchemaEntity.tags: @CollectionTable has no schema, expected \"SAMPLE\"",
                        "NoSchemaEntity: @Table has no schema, expected \"SAMPLE\"",
                        "NoTableEntity: @Entity without @Table",
                        "OtherSchemaEntity: @Table has schema \"IDENTITY\", expected \"SAMPLE\"",
                        "UnqualifiedCastEntity.moods: columnDefinition [\"IDENTITY\".\"SAMPLE_MOOD\"[]]"
                                + " must qualify every quoted name with \"SAMPLE\".",
                        "UnqualifiedCastEntity.moods: write [cast(? as \"SAMPLE_MOOD\"[])]"
                                + " must qualify every quoted name with \"SAMPLE\".",
                        "UnqualifiedTypeEntity.status: columnDefinition [\"SAMPLE_STATUS\"]"
                                + " must qualify every quoted name with \"SAMPLE\"."),
                EntitySchemaRule.violations(classes, "SAMPLE"));
    }

    @Test
    void reportsAPackageWithoutEntities() {
        JavaClasses classes = new ClassFileImporter().importPackages(FIXTURES + ".empty");

        assertEquals(
                List.of("no @Entity found, is the package right?"), EntitySchemaRule.violations(classes, "SAMPLE"));
    }
}
