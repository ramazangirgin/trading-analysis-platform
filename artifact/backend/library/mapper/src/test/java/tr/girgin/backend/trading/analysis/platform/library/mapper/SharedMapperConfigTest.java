package tr.girgin.backend.trading.analysis.platform.library.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

class SharedMapperConfigTest {

    private final SampleToSampleViewMapper mapper = Mappers.getMapper(SampleToSampleViewMapper.class);

    @Test
    void copiesWhatItMapsInsteadOfSharingIt() {
        Sample source = new Sample(new BigDecimal("12.50"), new Part("main"), List.of(new Part("a"), new Part("b")));

        SampleView copy = mapper.map(source);

        assertThat(copy.getPart()).isEqualTo(source.getPart()).isNotSameAs(source.getPart());
        assertThat(copy.getParts()).isEqualTo(source.getParts()).isNotSameAs(source.getParts());
        assertThat(copy.getParts().get(0)).isNotSameAs(source.getParts().get(0));
        assertThat(copy.getParts().get(1)).isNotSameAs(source.getParts().get(1));
    }

    @Test
    void changingTheCopyLeavesTheSourceAlone() {
        Sample source = new Sample(BigDecimal.ONE, new Part("main"), List.of(new Part("a")));

        mapper.map(source).getPart().setLabel("changed");

        assertThat(source.getPart().getLabel()).isEqualTo("main");
    }

    @Test
    void stillAppliesTheBuiltInConversion() {
        Sample source = new Sample(new BigDecimal("12.50"), new Part("main"), List.of());

        assertThat(mapper.map(source).getPrice()).isEqualTo("12.50");
    }

    @Mapper(config = SharedMapperConfig.class)
    interface SampleToSampleViewMapper {

        SampleView map(Sample source);
    }

    static class Sample {
        private BigDecimal price;
        private Part part;
        private List<Part> parts;

        Sample(BigDecimal price, Part part, List<Part> parts) {
            this.price = price;
            this.part = part;
            this.parts = parts;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public void setPrice(BigDecimal price) {
            this.price = price;
        }

        public Part getPart() {
            return part;
        }

        public void setPart(Part part) {
            this.part = part;
        }

        public List<Part> getParts() {
            return parts;
        }

        public void setParts(List<Part> parts) {
            this.parts = parts;
        }
    }

    static class SampleView {
        private String price;
        private Part part;
        private List<Part> parts;

        public String getPrice() {
            return price;
        }

        public void setPrice(String price) {
            this.price = price;
        }

        public Part getPart() {
            return part;
        }

        public void setPart(Part part) {
            this.part = part;
        }

        public List<Part> getParts() {
            return parts;
        }

        public void setParts(List<Part> parts) {
            this.parts = parts;
        }
    }

    static class Part {
        private String label;

        Part() {}

        Part(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Part part && Objects.equals(label, part.label);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(label);
        }
    }
}
