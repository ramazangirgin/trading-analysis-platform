class NamePrefix {
    void run() {
        Executors.newFixedThreadPool(2); // violation
        Executors.newCachedThreadPool(); // violation
        Executors.callable(task);
        Pools.newFixedThreadPool(2);
    }
}
