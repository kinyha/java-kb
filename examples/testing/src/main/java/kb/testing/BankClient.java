package kb.testing;

import org.springframework.stereotype.Component;

// Внешний банк. В тестах его подменяют.
@Component
public class BankClient {
    public String charge(String account, long cents) {
        throw new IllegalStateException("real bank is not reachable");
    }
}
