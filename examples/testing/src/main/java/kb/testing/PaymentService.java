package kb.testing;

import kb.testing.Payments.Payment;
import kb.testing.Payments.PaymentRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
    private final BankClient bank;
    private final PaymentRepository repository;

    PaymentService(BankClient bank, PaymentRepository repository) {
        this.bank = bank;
        this.repository = repository;
    }

    @Transactional
    public Payment pay(PaymentRequest r) {
        String status = bank.charge(r.account(), r.cents());
        return repository.save(r.account(), r.cents(), status);
    }
}
