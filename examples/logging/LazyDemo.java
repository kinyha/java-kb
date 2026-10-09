import org.slf4j.LoggerFactory;

public class LazyDemo {
    static int calls;
    static int renders;
    static final Object value = new Object() {
        @Override public String toString() { renders++; return "payload"; }
    };
    static Object expensive() { calls++; return value; }

    public static void main(String[] args) {
        var ctx = Support.reset(); // INFO, DEBUG disabled
        var capture = Support.capture(ctx, "CAPTURE");
        ctx.getLogger("ROOT").addAppender(capture);
        var log = LoggerFactory.getLogger("demo.payments");
        log.debug("payload {}", expensive());
        System.out.printf("placeholders: calls=%d renders=%d%n", calls, renders);
        calls = renders = 0;
        log.debug("payload " + expensive());
        System.out.printf("concatenation: calls=%d renders=%d%n", calls, renders);
        calls = renders = 0;
        log.atDebug().addArgument(() -> expensive()).log("payload {}");
        System.out.printf("supplier: calls=%d renders=%d%n", calls, renders);
        if (log.isDebugEnabled()) log.debug("payload {}", expensive());
        System.out.printf("guard: calls=%d renders=%d%n", calls, renders);
        log.atInfo().addArgument(() -> expensive()); // forgot final log(): no event
        System.out.println("builder without log(): events=" + capture.messages.size());

        var failure = new IllegalStateException("bank timeout");
        log.error("failed {}", "P7", failure); // final argument remains a cause
        log.error("failed {}", failure.getMessage()); // only text, no stack/cause
        log.atError().setCause(failure).addKeyValue("payment_id", "P7").log("failed");
        for (int i = 0; i < capture.messages.size(); i++) {
            System.out.printf("%s | cause=%s%n", capture.messages.get(i), capture.causes.get(i));
        }
        Support.check(capture.causes.equals(java.util.List.of("java.lang.IllegalStateException", "none", "java.lang.IllegalStateException")), "cause extraction");
        ctx.stop();
    }
}
