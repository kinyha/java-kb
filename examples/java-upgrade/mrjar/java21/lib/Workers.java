package lib;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Та же сигнатура, другая реализация: лежит в META-INF/versions/21/.
public class Workers {
    public static ExecutorService forIo() {
        return Executors.newVirtualThreadPerTaskExecutor(); // API с JDK 21
    }
}
