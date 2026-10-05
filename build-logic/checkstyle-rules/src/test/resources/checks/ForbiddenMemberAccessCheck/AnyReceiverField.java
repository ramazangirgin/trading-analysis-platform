class AnyReceiverField {
    void run() {
        Foo.INSTANCE.run(); // violation
        lookup().INSTANCE.run(); // violation
        Foo.OTHER.run();
        INSTANCE.run();
    }
}
