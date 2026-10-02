import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.Security;
import java.util.Arrays;

// Запуск: java DnsDemo.java   (JDK 21, нужен доступ к DNS)
public class DnsDemo {
    public static void main(String[] args) throws Exception {
        // Свойства кеша резолвера JVM; null — действует значение по умолчанию.
        System.out.println("networkaddress.cache.ttl=" + Security.getProperty("networkaddress.cache.ttl")
                + ", negative.ttl=" + Security.getProperty("networkaddress.cache.negative.ttl"));

        for (int i = 1; i <= 2; i++) {
            long t = System.nanoTime();
            InetAddress[] all = InetAddress.getAllByName("example.com");
            System.out.printf("lookup %d: %d addresses in %.1f ms%n", i, all.length, (System.nanoTime() - t) / 1e6);
        }

        for (int i = 1; i <= 2; i++) {
            long t = System.nanoTime();
            try {
                InetAddress.getByName("kb-demo-nonexistent-zzz-4242.com");
            } catch (UnknownHostException e) {
                System.out.printf("missing %d: UnknownHostException in %.1f ms%n", i, (System.nanoTime() - t) / 1e6);
            }
        }
    }
}
