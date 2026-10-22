import java.util.ArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class VirtualThreadsDemo {
    public static void main(String[] args) throws Exception {
        var permits = new Semaphore(2); // fixture for a scarce external resource, not a real DB pool
        var pair = new CyclicBarrier(2); // forces two holders to overlap without a timing benchmark
        var active = new AtomicInteger();
        var peak = new AtomicInteger();
        var tasks = new ArrayList<Future<Boolean>>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 12; i++) tasks.add(executor.submit(() -> {
                permits.acquire(); // waiting tasks do not each require an OS thread
                int users = active.incrementAndGet();
                peak.accumulateAndGet(users, Math::max);
                try {
                    pair.await(3, TimeUnit.SECONDS);
                    return Thread.currentThread().isVirtual();
                } finally { active.decrementAndGet(); permits.release(); }
            }));
            boolean allVirtual = true;
            for (var task : tasks) allVirtual &= task.get(5, TimeUnit.SECONDS);
            if (!allVirtual || peak.get() != 2 || active.get() != 0) throw new AssertionError();
            System.out.println("completed=" + tasks.size() + "; max resource users=" + peak.get()
                    + "; all virtual=" + allVirtual);
        }
    }
}
