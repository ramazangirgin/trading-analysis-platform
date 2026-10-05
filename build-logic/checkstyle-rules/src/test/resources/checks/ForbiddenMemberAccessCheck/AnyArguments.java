class AnyArguments {
    void run() throws Exception {
        Thread.sleep(1); // violation
        Thread.sleep(1, 2); // violation
        Thread.yield();
        Other.sleep(1);
        sleep(1);
        thread.sleep(1);
    }
}
