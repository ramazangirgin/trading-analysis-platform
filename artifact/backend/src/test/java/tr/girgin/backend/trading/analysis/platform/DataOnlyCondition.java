package tr.girgin.backend.trading.analysis.platform;

import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

final class DataOnlyCondition {

    /** The one model whose {@code toString} is written by hand: the generated one would print the hash. */
    private static final String MASKING_TO_STRING_OWNER =
            "tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.PasswordHash";

    private DataOnlyCondition() {}

    static ArchCondition<JavaClass> declareOnlyWhatTheCompilerGenerates() {
        return new ArchCondition<>("declare only what the compiler generates") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                Set<String> components = javaClass.getFields().stream()
                        .filter(field -> !field.getModifiers().contains(JavaModifier.STATIC))
                        .map(JavaField::getName)
                        .collect(Collectors.toSet());
                for (JavaMethod method : javaClass.getMethods()) {
                    if (!method.getModifiers().contains(JavaModifier.SYNTHETIC)
                            && !isGenerated(javaClass, method, components)) {
                        events.add(SimpleConditionEvent.violated(
                                javaClass, method.getFullName() + " is behaviour, not data"));
                    }
                }
                String superclass =
                        javaClass.getRawSuperclass().map(JavaClass::getName).orElse("");
                for (JavaConstructor constructor : javaClass.getConstructors()) {
                    for (JavaCall<?> call : constructor.getCallsFromSelf()) {
                        if (!call.getTargetOwner().getName().equals(superclass)) {
                            events.add(SimpleConditionEvent.violated(
                                    javaClass,
                                    constructor.getFullName() + " calls "
                                            + call.getTarget().getFullName()));
                        }
                    }
                }
            }
        };
    }

    private static boolean isGenerated(JavaClass javaClass, JavaMethod method, Set<String> components) {
        List<String> parameters =
                method.getRawParameterTypes().stream().map(JavaClass::getName).toList();
        return switch (method.getName()) {
            case "toString" ->
                parameters.isEmpty()
                        && (javaClass.getName().equals(MASKING_TO_STRING_OWNER) || isObjectMethodsBody(method));
            case "hashCode" -> parameters.isEmpty() && isObjectMethodsBody(method);
            case "equals" -> parameters.equals(List.of(Object.class.getName())) && isObjectMethodsBody(method);
            case "valueOf" -> javaClass.isEnum() && parameters.equals(List.of(String.class.getName()));
            default ->
                parameters.isEmpty()
                        && (components.contains(method.getName())
                                        && method.getCallsFromSelf().isEmpty()
                                || javaClass.isEnum() && method.getName().equals("values"));
        };
    }

    // The compiler writes a record's toString, hashCode and equals as one call to ObjectMethods.
    private static boolean isObjectMethodsBody(JavaMethod method) {
        try (InputStream in =
                method.getOwner().getSource().orElseThrow().getUri().toURL().openStream()) {
            ClassModel model = ClassFile.of().parse(in.readAllBytes());
            return model.methods().stream()
                    .filter(candidate -> candidate.methodName().equalsString(method.getName()))
                    .allMatch(candidate -> candidate
                            .code()
                            .map(code -> bodyIsObjectMethods(code.elementList()))
                            .orElse(false));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean bodyIsObjectMethods(List<CodeElement> elements) {
        List<CodeElement> calls = elements.stream()
                .filter(element -> element instanceof InvokeInstruction || element instanceof InvokeDynamicInstruction)
                .toList();
        return calls.size() == 1
                && calls.getFirst() instanceof InvokeDynamicInstruction indy
                && indy.invokedynamic()
                        .bootstrap()
                        .bootstrapMethod()
                        .reference()
                        .owner()
                        .asInternalName()
                        .equals("java/lang/runtime/ObjectMethods");
    }
}
