package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;

class RoleIdAttributeConverterTest {

    private final RoleIdAttributeConverter converter = new RoleIdAttributeConverter();

    @Test
    void roundTripsARoleId() {
        RoleId id = RoleId.newId();

        assertThat(converter.convertToDatabaseColumn(id)).isEqualTo(id.value());
        assertThat(converter.convertToEntityAttribute(id.value())).isEqualTo(id);
    }

    @Test
    void nullStaysNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
