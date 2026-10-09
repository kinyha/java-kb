import java.time.*;
import java.time.zone.ZoneRulesProvider;

public class TimelineDemo {
    public static void main(String[] args) {
        ZoneId berlin = ZoneId.of("Europe/Berlin");
        System.out.println("tzdb=" + ZoneRulesProvider.getVersions("Europe/Berlin").lastKey());
        LocalDateTime requested = LocalDateTime.parse("2024-10-27T02:30");
        ZonedDateTime early = requested.atZone(berlin);
        ZonedDateTime late = early.withLaterOffsetAtOverlap();
        System.out.println("valid offsets=" + berlin.getRules().getValidOffsets(requested));
        System.out.println("early=" + early + ", instant=" + early.toInstant());
        System.out.println("late=" + late + ", instant=" + late.toInstant());
        System.out.println("same fields=" + early.toLocalDateTime().equals(late.toLocalDateTime())
                + ", elapsed minutes=" + Duration.between(early, late).toMinutes());
        System.out.println("Tokyo same instant=" + early.withZoneSameInstant(ZoneId.of("Asia/Tokyo")));
        System.out.println("Tokyo same local=" + early.withZoneSameLocal(ZoneId.of("Asia/Tokyo")));

        OffsetDateTime a = OffsetDateTime.parse("2024-10-27T02:30+02:00");
        OffsetDateTime b = OffsetDateTime.parse("2024-10-27T00:30Z");
        System.out.println("offset equals=" + a.equals(b) + ", isEqual=" + a.isEqual(b)
                + ", compareTo=" + a.compareTo(b));
        if (!late.toInstant().equals(early.toInstant().plusSeconds(3600)))
            throw new AssertionError("overlap must distinguish two moments");
    }
}
