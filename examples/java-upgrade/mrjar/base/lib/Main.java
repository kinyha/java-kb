package lib;

import java.util.concurrent.ExecutorService;

public class Main {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Workers.forIo();             // какая Workers загрузится, решает JarFile
        pool.submit(() -> System.out.println("Java " + System.getProperty("java.specification.version")
                + ": " + Thread.currentThread())).get();
        pool.shutdown();
    }
}
