public class PatternDemo {
    sealed interface Outcome permits Paid, Rejected, Retry {}
    record Money(long minorUnits, String currency) {}
    record Paid(String id, Money amount) implements Outcome {}
    record Rejected(String reason) implements Outcome {}
    record Retry(int afterSeconds) implements Outcome {}

    static int legacyStatus(Outcome outcome) {
        if (outcome instanceof Paid) return 200;
        if (outcome instanceof Rejected) return 422;
        return 500; // silently treats a new Retry as an internal error
    }

    static String paidId(Object value) {
        if (!(value instanceof Paid p)) return "not paid";
        // Only paths where matching succeeded can reach this statement.
        return p.id();
    }

    static boolean positive(Object value) {
        return value instanceof Paid p && p.amount() != null
                && p.amount().minorUnits() > 0;
    }

    static String route(Outcome outcome) {
        return switch (outcome) {
            case null -> "bad request";
            case Paid(var id, Money(var units, var currency))
                    when units > 0 && "EUR".equals(currency) -> "book:" + id;
            case Paid p -> "manual"; // also handles null amount and failed guard
            case Rejected(var reason) -> "reject:" + reason;
            case Retry(var seconds) -> "retry:" + seconds;
        };
    }

    static String guardedOrder(Integer n) {
        return switch (n) {
            case Integer i when i > 0 -> "positive";
            case 1 -> "one"; // compiles, but the preceding guard already matches 1
            case Integer i -> "other";
        };
    }

    public static void main(String[] args) {
        Outcome retry = new Retry(30);
        System.out.println("legacy retry=" + legacyStatus(retry));
        Paid paid = new Paid("p1", new Money(100, "EUR"));
        System.out.println("id=" + paidId(paid) + ", positive=" + positive(paid));
        System.out.println("null instanceof=" + positive(null));
        System.out.println(route(paid));
        System.out.println(route(new Paid("p2", new Money(-1, "EUR"))));
        System.out.println(route(new Paid("p3", null)));
        System.out.println(route(new Rejected("limit")));
        System.out.println(route(retry));
        System.out.println(route(null));
        System.out.println("guarded 1=" + guardedOrder(1));
    }
}
