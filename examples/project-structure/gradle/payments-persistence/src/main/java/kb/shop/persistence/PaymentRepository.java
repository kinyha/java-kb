package kb.shop.persistence;

public interface PaymentRepository {              // public: иначе не видно модулю payments.
    long amount(String id);
}
