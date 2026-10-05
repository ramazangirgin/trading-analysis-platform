package tr.girgin.checkstyle.support;

import java.util.regex.Pattern;

/**
 * A field, a method or a constructor, written as a pattern: {@code Type#member}.
 *
 * <ul>
 *   <li>{@code System#out}: the field {@code out} of {@code System}.
 *   <li>{@code Type#method()}: a call of the method with any arguments; {@code Type#method(2)}: with exactly
 *       two arguments.
 *   <li>{@code Type#new()} / {@code Type#new(1)}: a constructor call, with any or with exactly one argument.
 *   <li>{@code *} as the type: any receiver, also one that is not a plain name ({@code *#printStackTrace()}).
 *   <li>{@code Type.*} as the type: a constant or field of the type as the receiver
 *       ({@code TimeUnit.*#sleep()} matches {@code TimeUnit.SECONDS.sleep(1)}).
 *   <li>{@code *} at the end of the member name: any name that starts so ({@code Executors#new*()}).
 * </ul>
 */
public final class MemberPattern {

    private static final String ANY = "*";
    private static final String CONSTRUCTOR_NAME = "new";
    private static final int ANY_ARGUMENTS = -1;
    private static final Pattern ARGUMENT_COUNT = Pattern.compile("\\d{1,6}");

    private enum Kind {
        FIELD,
        METHOD,
        CONSTRUCTOR
    }

    private final String source;
    private final Kind kind;
    /** {@code null}: any receiver. */
    private final TypeName receiver;
    /** The receiver is a member of {@link #receiver}, as in {@code TimeUnit.*}. */
    private final boolean receiverIsMemberOfType;

    private final String name;
    private final boolean namePrefix;
    private final int argumentCount;

    private MemberPattern(
            String source,
            Kind kind,
            TypeName receiver,
            boolean receiverIsMemberOfType,
            String name,
            boolean namePrefix,
            int argumentCount) {
        this.source = source;
        this.kind = kind;
        this.receiver = receiver;
        this.receiverIsMemberOfType = receiverIsMemberOfType;
        this.name = name;
        this.namePrefix = namePrefix;
        this.argumentCount = argumentCount;
    }

    /** Parses one pattern; throws {@link IllegalArgumentException} naming the pattern and what is wrong. */
    public static MemberPattern parse(String source) {
        try {
            return doParse(source);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid member pattern '" + source + "': " + e.getMessage(), e);
        }
    }

    private static MemberPattern doParse(String source) {
        int hash = source.indexOf('#');
        if (hash < 0) {
            throw new IllegalArgumentException("expected Type#member");
        }
        String type = source.substring(0, hash);
        String member = source.substring(hash + 1);

        int open = member.indexOf('(');
        boolean call = open >= 0;
        String name = call ? member.substring(0, open) : member;
        int argumentCount = call ? parseArgumentCount(member.substring(open)) : ANY_ARGUMENTS;
        if (!Identifiers.isNameOrPrefix(name)) {
            throw new IllegalArgumentException("'" + name + "' is not a member name");
        }
        Kind kind = !call ? Kind.FIELD : name.equals(CONSTRUCTOR_NAME) ? Kind.CONSTRUCTOR : Kind.METHOD;

        boolean memberOfType = type.endsWith("." + ANY);
        TypeName receiver = null;
        if (!type.equals(ANY)) {
            receiver = TypeName.parse(memberOfType ? type.substring(0, type.length() - 2) : type);
        }
        if (memberOfType && kind == Kind.CONSTRUCTOR) {
            throw new IllegalArgumentException("a constructor has no receiver, use Type#new()");
        }
        boolean prefix = name.endsWith(ANY);
        return new MemberPattern(
                source,
                kind,
                receiver,
                memberOfType,
                prefix ? name.substring(0, name.length() - 1) : name,
                prefix,
                argumentCount);
    }

    /** {@code (}, digits or nothing, {@code )}: the number of arguments, or any number when empty. */
    private static int parseArgumentCount(String parenthesized) {
        if (!parenthesized.endsWith(")")) {
            throw new IllegalArgumentException("expected ')' at the end");
        }
        String digits = parenthesized.substring(1, parenthesized.length() - 1);
        if (digits.isEmpty()) {
            return ANY_ARGUMENTS;
        }
        if (!ARGUMENT_COUNT.matcher(digits).matches()) {
            throw new IllegalArgumentException("'" + digits + "' is not an argument count");
        }
        return Integer.parseInt(digits);
    }

    /** The pattern as it was written. */
    public String source() {
        return source;
    }

    /**
     * Whether a reference to {@code field} on {@code receiverName} is selected.
     *
     * @param receiverName the dotted name the field is read from, {@code null} when the receiver is not a name
     */
    public boolean matchesField(String receiverName, String field, Imports imports) {
        return kind == Kind.FIELD && matchesReceiver(receiverName, imports) && matchesName(field);
    }

    /**
     * Whether a call of {@code method} on {@code receiverName} with {@code arguments} arguments is selected.
     *
     * @param receiverName the dotted name the method is called on, {@code null} when the receiver is not a name
     */
    public boolean matchesMethod(String receiverName, String method, int arguments, Imports imports) {
        return kind == Kind.METHOD
                && matchesReceiver(receiverName, imports)
                && matchesName(method)
                && matchesArguments(arguments);
    }

    /** Whether {@code new typeName(...)} with {@code arguments} arguments is selected. */
    public boolean matchesConstructor(String typeName, int arguments, Imports imports) {
        return kind == Kind.CONSTRUCTOR && matchesReceiver(typeName, imports) && matchesArguments(arguments);
    }

    private boolean matchesReceiver(String receiverName, Imports imports) {
        if (receiver == null) {
            return true;
        }
        if (receiverName == null) {
            return false;
        }
        if (!receiverIsMemberOfType) {
            return receiver.matches(receiverName, imports);
        }
        int lastDot = receiverName.lastIndexOf('.');
        return lastDot >= 0 && receiver.matches(receiverName.substring(0, lastDot), imports);
    }

    private boolean matchesName(String actual) {
        return namePrefix ? actual.startsWith(name) : actual.equals(name);
    }

    private boolean matchesArguments(int actual) {
        return argumentCount == ANY_ARGUMENTS || argumentCount == actual;
    }
}
