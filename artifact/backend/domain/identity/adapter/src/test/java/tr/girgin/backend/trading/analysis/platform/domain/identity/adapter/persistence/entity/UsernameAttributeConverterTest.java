package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;

class UsernameAttributeConverterTest {

    private final UsernameAttributeConverter converter = new UsernameAttributeConverter();

    @Test
    void roundTripsAUsernameKeepingItsCase() {
        Username username = new Username("Mixed.Case");

        assertThat(converter.convertToDatabaseColumn(username)).isEqualTo("Mixed.Case");
        assertThat(converter.convertToEntityAttribute("Mixed.Case")).isEqualTo(username);
    }

    @Test
    void nullStaysNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
