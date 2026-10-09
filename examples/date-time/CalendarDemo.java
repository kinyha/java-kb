import java.time.*;
import java.time.temporal.*;

public class CalendarDemo {
    public static void main(String[] args) {
        ZoneId berlin = ZoneId.of("Europe/Berlin");
        for (LocalDate date : new LocalDate[]{LocalDate.of(2024, 3, 30), LocalDate.of(2024, 10, 26)}) {
            ZonedDateTime start = date.atTime(12, 0).atZone(berlin);
            ZonedDateTime calendar = start.plus(Period.ofDays(1));
            ZonedDateTime elapsed = start.plus(Duration.ofDays(1));
            System.out.println("start=" + start);
            System.out.println("period=" + calendar + ", elapsed hours=" + Duration.between(start, calendar).toHours());
            System.out.println("duration=" + elapsed + ", elapsed hours=" + Duration.between(start, elapsed).toHours());
        }
        LocalDate anchor = LocalDate.of(2024, 1, 31);
        LocalDate feb = anchor.plusMonths(1);
        System.out.println("month once=" + feb + ", chained=" + feb.plusMonths(1)
                + ", anchored=" + anchor.plusMonths(2));
        System.out.println("month end=" + feb.plusMonths(1).with(TemporalAdjusters.lastDayOfMonth()));
        LocalDate begin = LocalDate.of(2024, 1, 1), end = LocalDate.of(2024, 3, 3);
        System.out.println("period=" + Period.between(begin, end) + ", day component="
                + Period.between(begin, end).getDays() + ", total days=" + ChronoUnit.DAYS.between(begin, end));
        LocalDate day = LocalDate.of(2024, 3, 31);
        Instant from = day.atStartOfDay(berlin).toInstant();
        Instant until = day.plusDays(1).atStartOfDay(berlin).toInstant();
        System.out.println("day interval=[" + from + ", " + until + "), hours=" + Duration.between(from, until).toHours());
        if (Duration.between(from, until).toHours() != 23) throw new AssertionError("local day is not 24h");
    }
}
