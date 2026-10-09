import org.slf4j.LoggerFactory;

public class ProviderDemo {
    public static void main(String[] args) {
        var log = LoggerFactory.getLogger("payments");
        log.info("payment accepted");
        System.out.println("factory=" + LoggerFactory.getILoggerFactory().getClass().getSimpleName());
    }
}
