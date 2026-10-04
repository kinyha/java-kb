// Реализация из чужого модуля: собрана против Gateway v1 и больше не пересобирается.
public class Sbp implements Gateway {
    public String pay(long amount) { return "sbp paid " + amount; }
}
