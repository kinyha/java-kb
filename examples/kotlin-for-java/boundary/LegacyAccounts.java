package pay;

// Старый Java-репозиторий: закрытый счёт возвращает null, аннотаций нет.
public class LegacyAccounts {
    public String findOwner(String accountId) {
        return accountId.startsWith("closed-") ? null : "Ivan Petrov";
    }
}
