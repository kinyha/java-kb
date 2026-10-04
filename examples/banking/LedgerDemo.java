import java.util.*;

public class LedgerDemo {
    enum Side { D, C }
    enum Kind {
        ASSET(true), LIABILITY(false), INCOME(false);
        final boolean debitNormal;
        Kind(boolean debitNormal) { this.debitNormal = debitNormal; }
    }
    record Account(Kind kind, String currency) {}
    record Line(String account, Side side, long minor) {}
    record Journal(String id, List<Line> lines) {
        Journal { lines = List.copyOf(lines); }
    }
    static final class Ledger {
        final Map<String, Account> accounts = Map.of(
            "cash", new Account(Kind.ASSET, "RUB"),
            "alice", new Account(Kind.LIABILITY, "RUB"),
            "bob", new Account(Kind.LIABILITY, "RUB"),
            "fee", new Account(Kind.INCOME, "RUB"));
        Map<String, Long> balances = new HashMap<>();
        final Map<String, Journal> journal = new LinkedHashMap<>();

        boolean post(Journal j) {
            if (j.id() == null || j.id().isBlank()) throw new IllegalArgumentException("empty id");
            Journal old = journal.get(j.id());
            if (old != null) {
                if (!old.equals(j)) throw new IllegalArgumentException("id conflict");
                return false; // Тот же ключ и те же строки: уже проведено.
            }
            if (j.lines().size() < 2) throw new IllegalArgumentException("too few lines");
            Map<String, Long> net = new HashMap<>();
            Map<String, Long> next = new HashMap<>(balances); // Пока меняем только копию.
            for (Line l : j.lines()) {
                Account a = accounts.get(l.account());
                if (a == null || l.side() == null || l.minor() <= 0)
                    throw new IllegalArgumentException("invalid line");
                long signed = l.side() == Side.D ? l.minor() : -l.minor();
                net.merge(a.currency(), signed, Math::addExact); // Каждая валюта отдельно.
                long delta = a.kind().debitNormal ? signed : -signed;
                next.merge(l.account(), delta, Math::addExact);
            }
            if (net.values().stream().anyMatch(n -> n != 0))
                throw new IllegalArgumentException("unbalanced");
            balances = next;
            journal.put(j.id(), j); // Учебная публикация: один поток, память, без БД.
            return true;
        }
        long balance(String account) { return balances.getOrDefault(account, 0L); }
    }
    static Journal opening() {
        return new Journal("opening", List.of(
            new Line("cash", Side.D, 100_000),
            new Line("alice", Side.C, 100_000)));
    }
    static Journal transfer(String id) {
        return new Journal(id, List.of(
            new Line("alice", Side.D, 12_300), // Обязательство банка перед Alice уменьшается.
            new Line("bob", Side.C, 12_000),   // Обязательство перед Bob растёт.
            new Line("fee", Side.C, 300)));   // Комиссионный доход растёт.
    }
    static Ledger opened() {
        Ledger l = new Ledger(); l.post(opening()); return l;
    }
    static void show(String name, Ledger l) {
        System.out.printf("%s alice=%d bob=%d fee=%d cash=%d journals=%d%n", name,
            l.balance("alice"), l.balance("bob"), l.balance("fee"),
            l.balance("cash"), l.journal.size());
    }
    static void brokenBalances() {
        long alice = 100_000, bob = 0;
        try {
            alice -= 12_000; // Первый независимый UPDATE успел завершиться.
            throw new IllegalStateException("crash before credit");
        } catch (IllegalStateException e) {
            System.out.printf("partial alice=%d bob=%d total=%d%n", alice, bob, alice + bob);
        }
    }
    static void normal() {
        Ledger l = opened(); l.post(transfer("pay-1")); show("posted", l);
        check(l.balance("alice") == 87_700 && l.balance("bob") == 12_000);
    }
    static void rejected() {
        Ledger l = opened();
        try {
            l.post(new Journal("broken", List.of(
                new Line("alice", Side.D, 12_300), new Line("bob", Side.C, 12_000))));
        } catch (IllegalArgumentException e) { System.out.println("rejected=" + e.getMessage()); }
        show("unchanged", l);
        check(l.balance("alice") == 100_000 && l.journal.size() == 1);
    }
    static void retries() {
        Ledger l = opened(); Journal payment = transfer("pay-1");
        System.out.println("first=" + l.post(payment));
        System.out.println("retry=" + l.post(payment));
        try {
            l.post(new Journal("pay-1", List.of(
                new Line("alice", Side.D, 12_400), new Line("bob", Side.C, 12_400))));
        } catch (IllegalArgumentException e) { System.out.println("conflict=" + e.getMessage()); }
        l.post(transfer("pay-2")); // Новый ключ: даже одинаковые суммы считаются новой операцией.
        show("new-key", l);
        check(l.balance("alice") == 75_400 && l.journal.size() == 3);
    }
    static void correction() {
        Ledger l = opened(); Journal original = transfer("pay-1"); l.post(original);
        List<Line> opposite = original.lines().stream()
            .map(x -> new Line(x.account(), x.side() == Side.D ? Side.C : Side.D, x.minor()))
            .toList();
        l.post(new Journal("reverse:pay-1", opposite)); // Новая запись, исходная сохранена.
        show("reversed", l);
        System.out.println("original-kept=" + l.journal.get("pay-1").equals(original));
        check(l.balance("alice") == 100_000 && l.balance("fee") == 0);
    }
    static void check(boolean ok) { if (!ok) throw new AssertionError(); }
    public static void main(String[] args) {
        brokenBalances(); normal(); rejected(); retries(); correction();
    }
}
