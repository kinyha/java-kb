public interface Gateway {
    String pay(long amount);
    default String refund(long amount) { return "refund not supported"; }   // добавлен default
}
