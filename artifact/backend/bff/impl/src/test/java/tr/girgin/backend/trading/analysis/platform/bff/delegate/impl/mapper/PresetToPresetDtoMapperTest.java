package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.PresetDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.PresetValuesDto;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.OwnerId;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.Preset;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.PresetId;

class PresetToPresetDtoMapperTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final PresetToPresetDtoMapper toDto = new PresetToPresetDtoMapperImpl();
    private final PresetValuesDtoToJsonStringMapper toJson = new PresetValuesDtoToJsonStringMapperImpl();

    PresetToPresetDtoMapperTest() {
        ReflectionTestUtils.setField(toDto, "jsonMapper", JSON);
        ReflectionTestUtils.setField(toJson, "jsonMapper", JSON);
    }

    @Test
    void valuesSurviveTheRoundTripThroughThePayload() {
        PresetValuesDto values =
                new PresetValuesDto("STOCK", List.of("market", "news"), "openai", "o3", "gpt-5.4", 2, 3, "en", true);

        PresetDto dto = toDto.map(preset(toJson.map(values)), Map.of());

        assertThat(dto.values()).isEqualTo(values);
    }

    @Test
    void ignoresAnUnknownPropertyOfAnOlderPayload() {
        PresetDto dto = toDto.map(preset("{\"assetType\":\"CRYPTO\",\"retired\":1}"), Map.of());

        assertThat(dto.values())
                .isEqualTo(new PresetValuesDto("CRYPTO", null, null, null, null, null, null, null, null));
    }

    @Test
    void writesNoModelFieldsWhenNoneWereSaved() {
        PresetValuesDto values =
                new PresetValuesDto("STOCK", List.of("market"), null, null, null, null, null, "en", null);

        String payload = toJson.map(values);

        assertThat(payload).doesNotContain("llmProvider", "maxDebateRounds", "null");
        assertThat(toDto.map(preset(payload), Map.of()).values()).isEqualTo(values);
    }

    @Test
    void namesTheOwner() {
        Preset preset = preset("{}");

        PresetDto dto = toDto.map(preset, Map.of(new UserId("u1"), new Username("alice")));

        assertThat(dto.owner().id()).isEqualTo("u1");
        assertThat(dto.owner().username()).isEqualTo("alice");
    }

    private static Preset preset(String payload) {
        return new Preset(new PresetId("p1"), "Mine", payload, new OwnerId("u1"), Instant.EPOCH, 0L);
    }
}
