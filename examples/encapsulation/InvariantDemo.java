import java.util.*;

// Запуск: java InvariantDemo.java   (JDK 21, без зависимостей)
public class InvariantDemo {
    // Анемичный счёт: данные наружу, правила — у вызывающих.
    static class OpenAccount {
        private long balance;
        private List<String> history = new ArrayList<>();
        public long getBalance() { return balance; }
        public void setBalance(long balance) { this.balance = balance; }
        public List<String> getHistory() { return history; }
    }

    // Инкапсулированный счёт: изменить состояние можно только через операции, которые держат инвариант.
    static final class Account {
        private long balance;
        private final List<String> history = new ArrayList<>();
        Account(long opening) {
            if (opening < 0) throw new IllegalArgumentException("opening < 0");
            this.balance = opening;
        }
        void debit(String paymentId, long amount) {
            if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
            if (amount > balance) throw new IllegalStateException("insufficient funds: " + balance + " < " + amount);
            balance -= amount;
            history.add("debit " + paymentId + " " + amount);
        }
        long balance() { return balance; }
        List<String> history() { return List.copyOf(history); }    // снимок, а не ссылка на внутренний список
    }

    public static void main(String[] args) {
        System.out.println("== open fields: every caller must remember the rules");
        OpenAccount open = new OpenAccount();
        open.setBalance(500);
        // сервис возвратов забыл проверку остатка
        open.setBalance(open.getBalance() - 800);
        // отчёт «почистил» историю для вывода
        open.getHistory().add("debit p-1 800");
        open.getHistory().clear();
        System.out.println("balance = " + open.getBalance() + ", history = " + open.getHistory());

        System.out.println("== invariant kept inside the class");
        Account acc = new Account(500);
        acc.debit("p-1", 300);
        try { acc.debit("p-2", 800); } catch (IllegalStateException e) { System.out.println("rejected: " + e.getMessage()); }
        try { acc.history().clear(); } catch (UnsupportedOperationException e) { System.out.println("history().clear(): UnsupportedOperationException"); }
        System.out.println("balance = " + acc.balance() + ", history = " + acc.history());
    }
}
