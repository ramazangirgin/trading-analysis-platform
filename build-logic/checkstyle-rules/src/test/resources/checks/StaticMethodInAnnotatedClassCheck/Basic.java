package demo;

import com.example.Marked;
import com.example.Other;

@Marked
public class FooBean {

    static final int LIMIT = 3;

    static int counter;

    public static void helper() {} // violation

    static String name() { // violation
        return "";
    }

    private static final synchronized void modifiers() {} // violation

    @Deprecated
    @SuppressWarnings("unused")
    static void annotated() {} // violation

    public void instance() {}

    static class Nested {
        static void inNested() {}
    }

    @Marked
    static class NestedMarked {
        static void inNestedMarked() {} // violation
    }

    void host() {
        class Local {
            static void inLocal() {}
        }
        Runnable anonymous = new Runnable() {
            public void run() {}
        };
    }
}

@Other
@Marked
class Both {
    static void twice() {} // violation
}

@Deprecated
class OnlyOther {
    static void notMarked() {}
}

class Bare {
    static void notAnnotated() {}
}

@Marked
interface MarkedInterface {
    static void inInterface() {}
}

@Marked
record MarkedRecord(int x) {
    static int create() {
        return 1;
    }
}

@Marked
enum MarkedEnum {
    ONE;

    static void inEnum() {}
}
