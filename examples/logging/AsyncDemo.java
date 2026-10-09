import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class AsyncDemo {
    static final class Gate extends AppenderBase<ILoggingEvent> {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch delivered;
        final List<String> messages = new ArrayList<>();
        final List<String> requests = new ArrayList<>();
        Gate(int expected) { delivered = new CountDownLatch(expected); }
        @Override protected void append(ILoggingEvent e) {
            if (messages.isEmpty()) {
                entered.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("gate timeout");
                } catch (InterruptedException ex) { throw new AssertionError(ex); }
            }
            messages.add(e.getFormattedMessage());
            requests.add(e.getMDCPropertyMap().get("request_id"));
            delivered.countDown();
        }
    }
    static boolean inQueuePut(Thread thread) {
        return thread.getState() == Thread.State.WAITING
                && java.util.Arrays.stream(thread.getStackTrace()).anyMatch(
                        f -> f.getClassName().equals("java.util.concurrent.ArrayBlockingQueue")
                                && f.getMethodName().equals("put"));
    }
    static void scenario(boolean neverBlock) throws Exception {
        var ctx = Support.reset();
        var sink = new Gate(neverBlock ? 11 : 12); sink.setContext(ctx); sink.setName("GATE"); sink.start();
        var async = new AsyncAppender();
        async.setContext(ctx); async.setName("ASYNC");
        async.setQueueSize(10); // start() derives default threshold 10 / 5 = 2
        async.setNeverBlock(neverBlock);
        async.setMaxFlushTime(5000); // after releasing the controlled gate
        async.addAppender(sink); async.start();
        ctx.getLogger("ROOT").addAppender(async);
        var log = LoggerFactory.getLogger("demo.payments");
        Thread blocked = null;
        try {
            MDC.put("request_id", "R7");
            log.info("hold"); // worker takes this event, then blocks in Gate
            Support.check(sink.entered.await(5, TimeUnit.SECONDS), "worker entered gate");
            for (int i = 1; i <= 12; i++) log.info("info {}", i);
            System.out.printf("neverBlock=%s: queued INFO=%d, threshold=%d%n",
                    neverBlock, async.getNumberOfElementsInQueue(), async.getDiscardingThreshold());
            Support.check(async.getNumberOfElementsInQueue() == 9, "strict remaining < 2");
            log.error("error 1"); // low-priority discard rule does not apply
            Support.check(async.getNumberOfElementsInQueue() == 10, "ERROR fills last slot");
            if (neverBlock) {
                log.error("error 2"); // offer() on full queue returns false
                System.out.println("full queue: error 2 call returned");
            } else {
                blocked = Thread.ofPlatform().start(() -> log.error("error 2"));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (!inQueuePut(blocked) && System.nanoTime() < deadline) Thread.sleep(1);
                boolean parked = inQueuePut(blocked);
                System.out.println("full queue: producer parked in ArrayBlockingQueue.put=" + parked);
                Support.check(parked, "producer really blocked in queue, not inferred from delay");
            }
            MDC.clear(); // queued event must keep its producer context
        } finally {
            sink.release.countDown();
            if (blocked != null) { blocked.join(5000); Support.check(!blocked.isAlive(), "producer completed"); }
            // stop() interrupts the worker. First let the controlled latch wait end normally.
            Support.check(sink.delivered.await(5, TimeUnit.SECONDS), "all expected events delivered");
            async.stop(); // worker drains and exits; makes captures visible to main
            MDC.clear();
        }
        long infos = sink.messages.stream().filter(s -> s.equals("hold") || s.startsWith("info ")).count();
        long errors = sink.messages.stream().filter(s -> s.startsWith("error ")).count();
        System.out.printf("kept INFO=%d/13, ERROR=%d/2; first MDC=%s%n", infos, errors, sink.requests.getFirst());
        Support.check(infos == 10 && errors == (neverBlock ? 1 : 2), "exact delivered counts");
        Support.check("R7".equals(sink.requests.getFirst()), "MDC snapshot taken before async handoff");
        ctx.stop();
    }
    public static void main(String[] args) throws Exception {
        var ctx = Support.reset();
        var defaults = new AsyncAppender();
        defaults.setContext(ctx); defaults.setName("DEFAULTS");
        defaults.addAppender(Support.capture(ctx, "UNUSED")); defaults.start();
        System.out.printf("defaults: queue=%d threshold=%d neverBlock=%s callerData=%s flush=%dms%n",
                defaults.getQueueSize(), defaults.getDiscardingThreshold(), defaults.isNeverBlock(),
                defaults.isIncludeCallerData(), defaults.getMaxFlushTime());
        defaults.stop(); ctx.stop();
        scenario(false);
        scenario(true);
    }
}
