import java.time.*;
import java.util.Date;

public class ClockDemo {
    static boolean expired(Instant deadline, Clock clock) {
        return !clock.instant().isBefore(deadline); // граница: now >= deadline
    }

    public static void main(String[] args) {
        Instant deadline = Instant.parse("2024-10-27T00:30:00Z");
        Clock justBefore = Clock.fixed(deadline.minusNanos(1), ZoneOffset.UTC);
        Clock atDeadline = Clock.fixed(deadline, ZoneOffset.UTC);
        System.out.println("before expired=" + expired(deadline, justBefore));
        System.out.println("at expired=" + expired(deadline, atDeadline));
        Clock tokyo = atDeadline.withZone(ZoneId.of("Asia/Tokyo"));
        System.out.println("clock same instant=" + atDeadline.instant().equals(tokyo.instant()));
        System.out.println("UTC fields=" + LocalDateTime.now(atDeadline) + ", Tokyo fields=" + LocalDateTime.now(tokyo));
        Instant precise = Instant.parse("2024-10-27T00:30:00.123456789Z");
        Instant restored = Date.from(precise).toInstant();
        System.out.println("legacy=" + restored + ", nanos lost=" + Duration.between(restored, precise).toNanos());
        long millis = deadline.toEpochMilli();
        System.out.println("epoch millis=" + millis + ", correct=" + Instant.ofEpochMilli(millis));
        System.out.println("wrong seconds=" + Instant.ofEpochSecond(millis));
        if (expired(deadline, justBefore) || !expired(deadline, atDeadline)) throw new AssertionError("deadline boundary");
    }
}
