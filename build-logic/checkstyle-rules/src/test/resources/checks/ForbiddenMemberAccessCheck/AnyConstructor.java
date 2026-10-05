class AnyConstructor {
    void run() {
        new Foo(); // violation
        new Bar(); // violation
        new Foo(1);
        new Foo[2];
    }
}
