package pay;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked                                         // всё в классе не-null, кроме помеченного
public class Accounts {
    public @Nullable String findOwner(String accountId) {
        return accountId.startsWith("closed-") ? null : "Ivan Petrov";
    }
    public String currency(String accountId) { return "RUB"; }
}
