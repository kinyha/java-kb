import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.filter.ThresholdFilter;

public class RoutesDemo {
    public static void main(String[] args) {
        var ctx = Support.reset();
        var root = ctx.getLogger("ROOT");
        root.setLevel(Level.ERROR);
        var payments = ctx.getLogger("demo.payments");
        payments.setLevel(Level.DEBUG);
        var log = ctx.getLogger("demo.payments.Bank"); // inherits DEBUG
        var local = Support.capture(ctx, "LOCAL");
        var shared = Support.capture(ctx, "SHARED");
        payments.addAppender(local);
        root.addAppender(shared);

        root.debug("must not be created");
        Support.check(local.messages.isEmpty() && shared.messages.isEmpty(), "root rejects own DEBUG");

        log.debug("accepted"); // root ERROR is not a second event-level check
        System.out.printf("root=ERROR, child=%s: LOCAL=%s, SHARED=%s%n",
                log.getEffectiveLevel(), local.messages, shared.messages);
        Support.check(shared.messages.equals(local.messages), "ancestor level must not reject");

        var threshold = new ThresholdFilter();
        threshold.setLevel("WARN");
        threshold.start();
        shared.addFilter(threshold); // appender filter actually rejects DEBUG
        local.messages.clear(); shared.messages.clear();
        log.debug("filtered");
        System.out.printf("SHARED threshold=WARN: LOCAL=%s, SHARED=%s%n", local.messages, shared.messages);
        Support.check(shared.messages.isEmpty(), "appender threshold");

        shared.clearAllFilters();
        payments.detachAppender(local);
        payments.addAppender(shared); // SAME appender now attached twice along route
        shared.messages.clear();
        log.info("duplicate");
        System.out.println("same appender on child+root: " + shared.messages);
        Support.check(shared.messages.size() == 2, "additivity duplicate");
        payments.setAdditive(false);
        shared.messages.clear();
        log.info("once");
        System.out.println("additivity=false on payments: " + shared.messages);
        Support.check(shared.messages.size() == 1, "stop at payments");
        System.out.printf("inherited unrelated=%s%n", ctx.getLogger("demo.other").getEffectiveLevel());
        ctx.stop();
    }
}
