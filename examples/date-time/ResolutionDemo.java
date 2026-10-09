import java.time.*;
import java.util.List;

public class ResolutionDemo {
    // Контракт ввода: неоднозначное или отсутствующее местное время отклоняем.
    static ZonedDateTime requireUnique(LocalDateTime local, ZoneId zone) {
        List<ZoneOffset> offsets = zone.getRules().getValidOffsets(local);
        if (offsets.size() != 1) throw new IllegalArgumentException("choose a valid unique moment");
        return ZonedDateTime.ofStrict(local, offsets.getFirst(), zone);
    }

    public static void main(String[] args) {
        ZoneId berlin = ZoneId.of("Europe/Berlin");
        LocalDateTime gap = LocalDateTime.parse("2024-03-31T02:30");
        System.out.println("gap offsets=" + berlin.getRules().getValidOffsets(gap));
        System.out.println("transition=" + berlin.getRules().getTransition(gap));
        System.out.println("atZone gap=" + gap.atZone(berlin));
        try { ZonedDateTime.ofStrict(gap, ZoneOffset.ofHours(1), berlin); }
        catch (DateTimeException expected) { System.out.println("strict gap rejected"); }
        for (String text : List.of("2024-03-31T02:30", "2024-10-27T02:30", "2024-10-27T04:30")) {
            try { System.out.println("unique=" + requireUnique(LocalDateTime.parse(text), berlin)); }
            catch (IllegalArgumentException expected) { System.out.println("unique rejected=" + text); }
        }
        LocalDateTime overlap = LocalDateTime.parse("2024-10-27T02:30");
        System.out.println("strict chosen=" + ZonedDateTime.ofStrict(overlap, ZoneOffset.ofHours(1), berlin));
        try { ZonedDateTime.ofStrict(overlap, ZoneOffset.ofHours(3), berlin); }
        catch (DateTimeException expected) { System.out.println("wrong offset rejected"); }
    }
}
