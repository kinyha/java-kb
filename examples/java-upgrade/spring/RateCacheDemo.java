import org.springframework.context.annotation.AnnotationConfigApplicationContext;

// Кэш курсов валют прогревается в @PostConstruct. Две копии бина различаются только импортом аннотации.
public class RateCacheDemo {
    public static class JavaxRateCache {
        boolean warmed;
        @javax.annotation.PostConstruct          // так было в Spring Boot 2 (Java EE, пакет javax)
        void warmUp() { warmed = true; }
    }

    public static class JakartaRateCache {
        boolean warmed;
        @jakarta.annotation.PostConstruct        // так нужно в Spring Boot 3+ (Jakarta EE 9+, пакет jakarta)
        void warmUp() { warmed = true; }
    }

    public static void main(String[] args) {
        try (var ctx = new AnnotationConfigApplicationContext(JavaxRateCache.class, JakartaRateCache.class)) {
            System.out.println("Spring " + org.springframework.core.SpringVersion.getVersion());
            System.out.println("javax   @PostConstruct called: " + ctx.getBean(JavaxRateCache.class).warmed);
            System.out.println("jakarta @PostConstruct called: " + ctx.getBean(JakartaRateCache.class).warmed);
        }
    }
}
