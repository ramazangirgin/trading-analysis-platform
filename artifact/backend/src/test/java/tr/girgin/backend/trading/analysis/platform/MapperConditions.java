package tr.girgin.backend.trading.analysis.platform;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class MapperConditions {

    private MapperConditions() {}

    static ArchCondition<JavaClass> beUsedByAnotherMapper() {
        return new ArchCondition<>("be used by another mapper") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                boolean used = javaClass.getDirectDependenciesToSelf().stream()
                        .map(dependency -> dependency.getOriginClass())
                        .filter(origin -> !origin.getName().matches(javaClass.getName() + "(Impl)?"))
                        .anyMatch(origin -> origin.getPackageName().endsWith(".mapper")
                                || origin.getPackageName().contains(".mapper."));
                if (!used) {
                    events.add(SimpleConditionEvent.violated(
                            javaClass, javaClass.getName() + " is not used by any other mapper"));
                }
            }
        };
    }

    static ArchCondition<JavaClass> haveASimpleNameNoOtherMapperHas() {
        return new ArchCondition<>("have a simple name no other mapper has") {
            private final Map<String, List<String>> namesBySimpleName = new HashMap<>();

            @Override
            public void init(Collection<JavaClass> allObjectsToTest) {
                for (JavaClass mapper : allObjectsToTest) {
                    namesBySimpleName
                            .computeIfAbsent(mapper.getSimpleName(), _ -> new ArrayList<>())
                            .add(mapper.getName());
                }
            }

            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                List<String> others = namesBySimpleName.get(javaClass.getSimpleName()).stream()
                        .filter(name -> !name.equals(javaClass.getName()))
                        .toList();
                if (!others.isEmpty()) {
                    events.add(SimpleConditionEvent.violated(
                            javaClass, javaClass.getName() + " has the same name as " + String.join(", ", others)));
                }
            }
        };
    }
}
