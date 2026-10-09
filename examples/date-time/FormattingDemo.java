import java.time.*;
import java.time.format.*;
import java.util.Locale;

public class FormattingDemo {
    public static void main(String[] args) {
        DateTimeFormatter smart = DateTimeFormatter.ofPattern("uuuu-MM-dd", Locale.ROOT);
        DateTimeFormatter strict = smart.withResolverStyle(ResolverStyle.STRICT);
        DateTimeFormatter lenient = smart.withResolverStyle(ResolverStyle.LENIENT);
        System.out.println("smart=" + LocalDate.parse("2023-02-29", smart));
        System.out.println("lenient=" + LocalDate.parse("2023-02-29", lenient));
        try { LocalDate.parse("2023-02-29", strict); }
        catch (DateTimeParseException expected) { System.out.println("strict invalid date rejected"); }
        System.out.println("strict leap=" + LocalDate.parse("2024-02-29", strict));
        try { LocalDate.parse("2024-02-29", DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
                .withResolverStyle(ResolverStyle.STRICT)); }
        catch (DateTimeParseException expected) { System.out.println("strict year-of-era without era rejected"); }
        LocalDate lastDay = LocalDate.of(2021, 12, 31);
        System.out.println("calendar year=" + lastDay.format(DateTimeFormatter.ofPattern("uuuu-MM-dd", Locale.US)));
        System.out.println("US week year=" + lastDay.format(DateTimeFormatter.ofPattern("YYYY-MM-dd", Locale.US)));
        System.out.println("UK week year=" + lastDay.format(DateTimeFormatter.ofPattern("YYYY-MM-dd", Locale.UK)));
        String text = "2024-10-27T02:30:00+02:00";
        System.out.println("protocol instant=" + OffsetDateTime.parse(text).toInstant());
        try { Instant.parse("2024-10-27T02:30:00"); }
        catch (DateTimeParseException expected) { System.out.println("instant without offset rejected"); }
    }
}
