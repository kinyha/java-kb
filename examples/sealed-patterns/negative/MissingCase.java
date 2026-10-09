class MissingCase {
    sealed interface Outcome permits Paid, Rejected, Retry {}
    record Paid() implements Outcome {}
    record Rejected() implements Outcome {}
    record Retry() implements Outcome {}
    static int status(Outcome value) {
        return switch (value) {
            case Paid p -> 200;
            case Rejected r -> 422;
        }; // Retry is not covered
    }
}
