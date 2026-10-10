package kb.javatesting;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class PaymentService {
    public record Receipt(String id, long amount, long fee, Instant at) {}
    public interface Bank { boolean charge(String id, long amount); }
    public interface Repository { void save(Receipt receipt); }
    public static final class Declined extends RuntimeException {
        public Declined() { super("bank declined"); }
    }
    private final FeePolicy fees;
    private final Bank bank;
    private final Repository repository;
    private final Clock clock;
    public PaymentService(FeePolicy fees, Bank bank, Repository repository, Clock clock) {
        this.fees = fees; this.bank = bank; this.repository = repository; this.clock = clock;
    }
    public Receipt pay(String id, long amount) {
        Objects.requireNonNull(id, "id");
        long fee = fees.cents(amount); // real policy validates BEFORE external charge
        if (!bank.charge(id, amount)) throw new Declined();
        var receipt = new Receipt(id, amount, fee, clock.instant());
        repository.save(receipt);
        return receipt;
    }
}
