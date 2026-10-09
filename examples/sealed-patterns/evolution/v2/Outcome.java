public sealed interface Outcome permits Paid, Rejected, Retry {}
record Paid() implements Outcome {}
record Rejected() implements Outcome {}
record Retry() implements Outcome {}
