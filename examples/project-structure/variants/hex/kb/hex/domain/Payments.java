package kb.hex.domain;

public interface Payments {                       // Исходящий порт: его реализует адаптер из database.
    void save(Payment payment);
    Payment find(String id);
}
