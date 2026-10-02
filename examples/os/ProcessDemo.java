import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ProcessDemo {
    private static int captured = 41;
    private static final int LOG_BLOCKS = 32768;
    private static final int BLOCK_BYTES = 1024;

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("threads|pipe|signals");
        switch (args[0]) {
            case "threads" -> threads();
            case "pipe" -> pipe();
            case "signals" -> signals();
            case "log-child" -> logChild();
            case "signal-child" -> signalChild();
            default -> throw new IllegalArgumentException("Unknown mode");
        }
    }

    private static void threads() throws InterruptedException {
        Thread worker = Thread.ofPlatform().name("payment-worker").unstarted(() -> {
            captured = 99;                 // Общая память одного процесса.
            System.out.println("worker captured=" + captured);
            throw new IllegalStateException("bank refused");
        });
        worker.setUncaughtExceptionHandler((thread, error) ->
                System.out.println("worker failed=" + error.getClass().getSimpleName()));
        worker.start();
        worker.join();                     // Даёт main видимость записи worker.
        System.out.println("main after join: captured=" + captured + ", alive=true");
    }

    private static Process child(String mode) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String source = Path.of("ProcessDemo.java").toRealPath().toString();
        return new ProcessBuilder(java, source, mode).start(); // Отдельная JVM, три канала.
    }

    private static BufferedReader stdout(Process process) {
        return new BufferedReader(new InputStreamReader(process.getInputStream()));
    }

    private static void pipe() throws Exception {
        Process process = child("log-child");
        try (BufferedReader output = stdout(process)) {
            process.getOutputStream().close();
            System.out.println("ready=" + output.readLine());
            boolean finished = process.waitFor(150, TimeUnit.MILLISECONDS);
            System.out.println("finished without reading stderr=" + finished);
            if (finished) throw new AssertionError("Child should block on its unread stderr");

            long bytes = process.getErrorStream().transferTo(OutputStream.nullOutputStream());
            System.out.println("drained stderr bytes=" + bytes);
            if (bytes != (long) LOG_BLOCKS * BLOCK_BYTES) throw new AssertionError("Wrong log size");
            boolean done = process.waitFor(5, TimeUnit.SECONDS);
            System.out.println("finished after drain=" + done);
            if (!done) throw new AssertionError("Child did not exit");
            System.out.println("stdout=" + output.readLine());
            System.out.println("exit=" + process.exitValue());
        } finally {
            if (process.isAlive()) { process.destroyForcibly(); process.waitFor(); }
        }
    }

    private static void logChild() throws Exception {
        System.out.println("child ready");
        System.out.flush();
        byte[] block = new byte[BLOCK_BYTES];
        for (int i = 0; i < LOG_BLOCKS; i++) System.err.write(block);
        System.err.flush();
        System.out.println("child done");
    }

    private static void signals() throws Exception {
        stop(false);
        stop(true);
    }

    private static void stop(boolean force) throws Exception {
        Process process = child("signal-child");
        try (BufferedReader output = stdout(process)) {
            process.getOutputStream().close();
            if (!"child ready".equals(output.readLine())) throw new AssertionError("Child not ready");
            if (!process.supportsNormalTermination())
                throw new IllegalStateException("This demo needs Unix normal termination");
            String operation = force ? "destroyForcibly" : "destroy";
            // ProcessHandle посылает сигнал, а потоки stdout остаются у объекта Process.
            boolean sent = force ? process.toHandle().destroyForcibly() : process.toHandle().destroy();
            if (!sent) throw new AssertionError("Termination request failed");
            boolean exited = process.waitFor(5, TimeUnit.SECONDS);
            if (!exited) throw new AssertionError("Child did not exit");
            String hook = output.readLine();
            System.out.println(operation + " hook=" + (hook == null ? "<none>" : hook));
            System.out.println(operation + " exited=" + exited);
        } finally {
            if (process.isAlive()) { process.destroyForcibly(); process.waitFor(); }
        }
    }

    private static void signalChild() throws InterruptedException {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("hook ran");
            System.out.flush();
        }));
        System.out.println("child ready");
        System.out.flush();
        new CountDownLatch(1).await();      // Ждёт сигнал ОС, а не interrupt от родителя.
    }
}
