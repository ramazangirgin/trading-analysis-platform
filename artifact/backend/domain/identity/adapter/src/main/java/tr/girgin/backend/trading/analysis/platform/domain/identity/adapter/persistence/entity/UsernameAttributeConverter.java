package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;

/** A {@link Username} as the text of its column. Applied with {@code @Convert}, not automatically. */
@Converter
public class UsernameAttributeConverter implements AttributeConverter<Username, String> {

    @Override
    public String convertToDatabaseColumn(Username attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public Username convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new Username(dbData);
    }
}
