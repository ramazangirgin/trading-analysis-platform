package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.PasswordHash;

class PasswordHashAttributeConverterTest {

    private final PasswordHashAttributeConverter converter = new PasswordHashAttributeConverter();

    @Test
    void roundTripsAPasswordHash() {
        PasswordHash hash = new PasswordHash("{bcrypt}$2a$10$hash");

        assertThat(converter.convertToDatabaseColumn(hash)).isEqualTo("{bcrypt}$2a$10$hash");
        assertThat(converter.convertToEntityAttribute("{bcrypt}$2a$10$hash")).isEqualTo(hash);
    }

    @Test
    void nullStaysNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
