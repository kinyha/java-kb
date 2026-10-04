import java.util.*;

public class PaymentReviewDemo {
    record Request(String key, long amount) {}

    static final class TemporaryFailure extends RuntimeException {}
    static final class ReplyLost extends RuntimeException {}
    static final class PayloadConflict extends RuntimeException {}

    static final class Bank {
        final Map<String, Long> accepted = new HashMap<>();
        long charged;
        int calls;
        boolean failBefore, loseReply;

        String charge(Request request, boolean useKey) {
            calls++;
            if (failBefore) {
                failBefore = false;
                throw new TemporaryFailure();     // эффект ещё не произошёл
            }
            if (useKey && accepted.containsKey(request.key())) {
                if (accepted.get(request.key()) != request.amount()) {
                    throw new PayloadConflict();   // тот же ключ с другой суммой запрещён
                }
                return "REPLAY";                  // прошлый эффект не выполняется повторно
            }
            charged = Math.addExact(charged, request.amount());
            if (useKey) accepted.put(request.key(), request.amount());
            if (loseReply) {
                loseReply = false;
                throw new ReplyLost();             // эффект есть, клиент ответа не увидел
            }
            return "CHARGED";
        }
    }

    static final class Handler {
        final Set<String> done = new HashSet<>();
        final Bank bank;
        final boolean markFirst, useKey;

        Handler(Bank bank, boolean markFirst, boolean useKey) {
            this.bank = bank;
            this.markFirst = markFirst;
            this.useKey = useKey;
        }

        String pay(Request request) {
            if (done.contains(request.key())) return "SKIPPED";
            if (markFirst) done.add(request.key());   // пока лишь локальная отметка
            String result = bank.charge(request, useKey);
            if (!markFirst) done.add(request.key());  // сюда не попадём при потере ответа
            return result;
        }
    }

    static String attempt(Handler handler, Request request) {
        try {
            return handler.pay(request);
        } catch (TemporaryFailure e) {
            return "TEMPORARY_FAILURE";
        } catch (ReplyLost e) {
            return "REPLY_LOST";
        }
    }

    static void markBefore() {
        Bank bank = new Bank();
        bank.failBefore = true;
        Handler handler = new Handler(bank, true, false);
        Request request = new Request("k7", 100);
        System.out.println("== mark before external effect ==");
        System.out.println("first=" + attempt(handler, request));
        System.out.println("retry=" + attempt(handler, request));
        System.out.println("done=" + handler.done.contains("k7"));
        System.out.println("bank calls=" + bank.calls + " charged=" + bank.charged);
    }

    static void markAfter() {
        Bank bank = new Bank();
        bank.loseReply = true;
        Handler handler = new Handler(bank, false, false);
        Request request = new Request("k7", 100);
        System.out.println("== external effect before mark ==");
        System.out.println("first=" + attempt(handler, request));
        System.out.println("retry=" + attempt(handler, request));
        System.out.println("done=" + handler.done.contains("k7"));
        System.out.println("bank calls=" + bank.calls + " charged=" + bank.charged);
    }

    static void providerKey() {
        Bank bank = new Bank();
        bank.loseReply = true;
        Handler handler = new Handler(bank, false, true);
        Request request = new Request("k7", 100);
        System.out.println("== same key reaches the bank ==");
        System.out.println("first=" + attempt(handler, request));
        System.out.println("retry=" + attempt(handler, request));
        System.out.println("bank calls=" + bank.calls + " charged=" + bank.charged);
    }

    static void changedPayload() {
        Bank bank = new Bank();
        bank.charge(new Request("k7", 100), true);
        System.out.println("== the key also binds the payload ==");
        try {
            bank.charge(new Request("k7", 200), true);
            throw new AssertionError("conflict expected");
        } catch (PayloadConflict expected) {
            System.out.println("same key, new amount=CONFLICT");
        }
        System.out.println("charged=" + bank.charged);
    }

    static void testEvidence() {
        Bank bank = new Bank();
        bank.failBefore = true;
        Handler handler = new Handler(bank, true, false);
        Request request = new Request("k7", 100);
        attempt(handler, request);
        attempt(handler, request);
        System.out.println("== green test of the wrong property ==");
        System.out.println("marker test=" + (handler.done.contains("k7") ? "PASS" : "FAIL"));
        System.out.println("payment test=" + (bank.charged == 100 ? "PASS" : "FAIL"));
        System.out.println("expected charged=100, actual=" + bank.charged);
    }

    public static void main(String[] args) {
        markBefore();
        markAfter();
        providerKey();
        changedPayload();
        testEvidence();
    }
}
