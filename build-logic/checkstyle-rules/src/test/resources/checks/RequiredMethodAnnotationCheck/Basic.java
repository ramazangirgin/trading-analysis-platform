package demo;

import com.example.Required;

class FooDelegate {

    FooDelegate() {}

    public FooDelegate(int x) {}

    public void bare() {} // violation

    @Override
    public String toString() { // violation
        return "";
    }

    @Deprecated
    @SuppressWarnings("unused")
    public void unrelated() {} // violation

    public static void staticBare() {} // violation

    public final synchronized void modifiers() {} // violation

    @Required
    public void annotated() {}

    @Deprecated
    @Required
    public void annotatedAmongOthers() {}

    @com.example.Required
    public void annotatedQualified() {}

    @Required("a reason")
    public void annotatedWithValue() {}

    void packagePrivate() {}

    protected void protectedMethod() {}

    private void privateMethod() {}

    static class Inner {
        public void nested() {}
    }

    class InnerDelegate {
        public void nestedAndMatching() {} // violation
    }

    void host() {
        class LocalHelper {
            public void local() {}
        }
        Runnable anonymous = new Runnable() {
            public void run() {}
        };
    }
}

class Unrelated {
    public void notChecked() {}
}

interface BarDelegate {
    void declared();

    default void defaulted() {}
}

record BazDelegate(int x) {
    public int twice() {
        return x * 2;
    }
}

enum QuxDelegate {
    ONE {
        public void body() {}
    };

    public void inEnum() {}
}
