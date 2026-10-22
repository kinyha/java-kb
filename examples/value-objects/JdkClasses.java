import java.math.BigDecimal;
import java.time.*;
import java.util.*;

// JDK 28: java --enable-preview JdkClasses.java
public class JdkClasses {
    public static void main(String[] args) {
        Object[] samples = { 42, 42L, 4.2, true, Optional.of("x"), LocalDate.now(), Instant.now(), Duration.ZERO,
                "RUB", new BigDecimal("4.20"), UUID.randomUUID(), ZoneOffset.UTC, List.of(1) };
        for (Object x : samples)
            System.out.printf("%-14s %s%n", x.getClass().getSimpleName(), Objects.hasIdentity(x) ? "identity" : "value");
    }
}
