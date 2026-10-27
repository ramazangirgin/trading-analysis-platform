package tr.girgin.backend.trading.analysis.platform.library.mapper;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.mapstruct.control.MappingControl;

@Retention(RetentionPolicy.CLASS)
@MappingControl(MappingControl.Use.MAPPING_METHOD)
@MappingControl(MappingControl.Use.BUILT_IN_CONVERSION)
public @interface DeepCloneWithConversions {}
