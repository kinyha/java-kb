import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import org.slf4j.MDC;

public class MdcDemo {
    static void install(Map<String, String> map) {
        if (map == null) MDC.clear(); else MDC.setContextMap(map);
    }
    static Runnable contextual(Runnable task) {
        var submitted = MDC.getCopyOfContextMap(); // caller NOW, not inside run()
        return () -> {
            var previous = MDC.getCopyOfContextMap(); // worker's enclosing scope
            try {
                install(submitted);
                task.run();
            } finally {
                install(previous); // also when task fails
            }
        };
    }
    public static void main(String[] args) throws Exception {
        Support.reset();
        try (var pool = Executors.newSingleThreadExecutor()) {
            // get() fixes ordering: the same worker executes A before B.
            pool.submit(() -> MDC.put("request_id", "A")).get();
            System.out.println("bad request B sees=" + pool.submit(() -> MDC.get("request_id")).get());
            pool.submit(MDC::clear).get();
            MDC.put("request_id", "MAIN");
            System.out.println("plain pool sees=" + pool.submit(() -> MDC.get("request_id")).get());
            Thread.ofVirtual().start(() -> System.out.println("new virtual thread sees=" + MDC.get("request_id"))).join();

            pool.submit(() -> MDC.put("request_id", "OUTER")).get();
            MDC.put("request_id", "P7");
            Runnable wrapped = contextual(() -> {
                System.out.println("wrapped sees=" + MDC.get("request_id"));
                throw new IllegalStateException("bank timeout");
            });
            MDC.put("request_id", "P8"); // after capture, before execution
            try { pool.submit(wrapped).get(); } catch (ExecutionException e) {
                System.out.println("task cause=" + e.getCause().getClass().getSimpleName());
            }
            String restored = pool.submit(() -> MDC.get("request_id")).get();
            System.out.println("worker after failure=" + restored);
            Support.check("OUTER".equals(restored), "restored worker scope");
            System.out.println("caller still=" + MDC.get("request_id"));
            pool.submit(MDC::clear).get();
        } finally { MDC.clear(); }

        MDC.put("request_id", "OUTER");
        try (var ignored = MDC.putCloseable("request_id", "INNER")) {
            System.out.println("closeable inside=" + MDC.get("request_id"));
        }
        System.out.println("closeable after=" + MDC.get("request_id"));
        Support.check(MDC.get("request_id") == null, "putCloseable removes key");
        MDC.clear();
    }
}
