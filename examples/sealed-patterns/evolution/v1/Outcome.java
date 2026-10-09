public sealed interface Outcome permits Paid, Rejected {}
record Paid() implements Outcome {}
record Rejected() implements Outcome {}
