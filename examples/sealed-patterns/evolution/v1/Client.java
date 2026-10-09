public class Client {
    public static String route(Outcome value) {
        return switch (value) {
            case Paid p -> "paid";
            case Rejected r -> "rejected";
        };
    }
}
