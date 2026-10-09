package tr.girgin.backend.trading.analysis.platform.library.persistence.fixture.entities;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Set;
import org.hibernate.annotations.ColumnTransformer;

/** Fixture of EntitySchemaRuleTest: the schema "SAMPLE" everywhere, so no violation. */
// Fixture: the rule reads the annotations, nothing reads the fields, so they are not private-with-accessors.
@SuppressWarnings("checkstyle:VisibilityModifier")
@Entity
@Table(name = "CORRECT", schema = "SAMPLE")
public class CorrectEntity {

    @Id
    @Column(name = "ID")
    String id;

    @Column(name = "MOODS", columnDefinition = "\"SAMPLE\".\"SAMPLE_MOOD\"[]")
    @ColumnTransformer(write = "cast(? as \"SAMPLE\".\"SAMPLE_MOOD\"[])")
    String[] moods;

    @ElementCollection
    @CollectionTable(name = "CORRECT_TAGS", schema = "SAMPLE")
    Set<String> tags;
}
