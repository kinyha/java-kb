import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class JsonDemo {
    public static void main(String[] args) {
        var log = LoggerFactory.getLogger("demo.payments");
        MDC.put("request_id", "R7");
        try {
            log.atInfo().addKeyValue("payment_id", "P7").addKeyValue("attempt", 2)
                    .log("bank reply {}", "ok\n\"level\":\"ERROR\"");
        } finally { MDC.clear(); }
    }
}
