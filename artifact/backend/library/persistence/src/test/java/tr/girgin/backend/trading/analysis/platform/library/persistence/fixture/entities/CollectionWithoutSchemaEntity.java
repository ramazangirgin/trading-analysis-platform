package tr.girgin.backend.trading.analysis.platform.library.persistence.fixture.entities;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Set;

/** Fixture of EntitySchemaRuleTest: a {@code @CollectionTable} without a schema. */
// Fixture: the rule reads the annotations, nothing reads the fields, so they are not private-with-accessors.
@SuppressWarnings("checkstyle:VisibilityModifier")
@Entity
@Table(name = "COLLECTION", schema = "SAMPLE")
public class CollectionWithoutSchemaEntity {

    @Id
    String id;

    @ElementCollection
    @CollectionTable(name = "COLLECTION_TAGS")
    Set<String> tags;
}
