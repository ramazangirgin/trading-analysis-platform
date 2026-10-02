package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.eventline.mapper;

import java.util.Locale;
import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Rating;

/**
 * Lenient: "Overweight", "BUY", "hold." and "**Sell**" all map; anything else is REVIEW, the
 * same fallback upstream uses for a decision without a parseable rating.
 */
@Mapper
public interface StringToRatingMapper {

    default Rating map(String source) {
        if (source == null) {
            return null;
        }
        String letters = source.replaceAll("[^A-Za-z]", "").toUpperCase(Locale.ROOT);
        for (Rating rating : Rating.values()) {
            if (rating.name().equals(letters)) {
                return rating;
            }
        }
        return Rating.REVIEW;
    }
}
