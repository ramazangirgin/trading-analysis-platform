class ConstructorArguments {
    void run() {
        new Random(7); // violation
        new Random();
        new Random(1, 2);
        Random.of(7);
    }
}
