class QualifiedJavaLang {
    void run() {
        System.out.println(); // violation
        java.lang.System.out.println(); // violation
        sys.System.out.println();
        lang.System.out.println();
    }
}
