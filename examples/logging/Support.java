import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.LoggerFactory;

final class Support {
    static LoggerContext reset() {
        var ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        ctx.reset();
        ctx.getLogger("ROOT").setLevel(Level.INFO);
        return ctx;
    }

    static final class Capture extends AppenderBase<ILoggingEvent> {
        final List<String> messages = new ArrayList<>();
        final List<String> causes = new ArrayList<>();
        @Override protected void append(ILoggingEvent e) {
            // Resolve immediately: do not retain an event with lazy fields.
            messages.add(e.getFormattedMessage());
            causes.add(e.getThrowableProxy() == null ? "none" : e.getThrowableProxy().getClassName());
        }
    }

    static Capture capture(LoggerContext ctx, String name) {
        var app = new Capture();
        app.setContext(ctx);
        app.setName(name);
        app.start();
        return app;
    }
    static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
