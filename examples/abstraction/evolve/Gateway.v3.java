public interface Gateway {
    String pay(long amount);
    String refund(long amount);                                             // добавлен абстрактный
}
