package lib;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Базовая версия: компилируется с --release 8 и лежит в корне JAR.
public class Workers {
    public static ExecutorService forIo() {
        return Executors.newFixedThreadPool(64);           // на Java 8 виртуальных потоков нет
    }
}
