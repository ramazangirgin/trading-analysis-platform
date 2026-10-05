import static java.lang.System.out;

class SystemStreams {
    void run(Printer printer, Other other) {
        System.out.println("a"); // violation
        System.err.println("b"); // violation
        java.lang.System.out.println("c"); // violation
        Runnable r = System.out::println; // violation
        String s = String.valueOf(System.out); // violation
        PrintStream p = (System.err); // violation
        System.in.read();
        System.getProperty("out");
        other.out.println("d");
        Other.out.println("e");
        printer.err();
        out.println("f");
        String text = "System.out.println()";
        // System.out.println("g");
        /* System.err.println("h"); */
    }

    void local() {
        Printer out = null;
        out.println();
    }
}
