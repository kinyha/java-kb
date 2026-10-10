import java.util.*;

public class ToolBoundaryDemo {
    enum Op { READ, REFUND }
    record Request(Op op, String invoice, long cents, int version, boolean modelSaysApproved) {}
    record Invoice(String tenant, long refundable, int version) {}
    record Approval(String principal, String task, String invoice, long cents, int version) {}
    // Obtained from authentication, task UI and approval storage, never deserialized from model output.
    record Context(String tenant, String principal, String task, Set<Op> permissions, Set<Op> taskOps, Set<String> targets) {}
    static final class Gate {
        final Map<String, Invoice> invoices = new HashMap<>(Map.of(
                "P7", new Invoice("A", 100, 4), "P8", new Invoice("B", 200, 2)));
        final Set<Approval> approvals = new HashSet<>(); // trusted server-side storage, sequential demo
        int effects;
        static boolean shape(Request r) {
            return r != null && r.op() != null && r.invoice() != null
                    && r.invoice().matches("P[0-9]+") && r.version() >= 0
                    && (r.op() == Op.READ ? r.cents() == 0 : r.cents() > 0);
        }
        String execute(Context c, Request r) {
            if (!shape(r)) return "DENY shape";
            if (!c.permissions().contains(r.op())) return "DENY permission";
            if (!c.taskOps().contains(r.op())) return "DENY task";
            var invoice = invoices.get(r.invoice());
            if (invoice == null || !invoice.tenant().equals(c.tenant())) return "DENY owner";
            if (!c.targets().contains(r.invoice())) return "DENY target";
            if (r.version() != invoice.version()) return "DENY stale version";
            if (r.op() == Op.READ) return "READ " + r.invoice() + " refundable=" + invoice.refundable();
            if (r.cents() > invoice.refundable()) return "DENY amount";
            // A model's approved=true is NOT consulted. Exact action must exist in trusted storage.
            if (!approvals.remove(new Approval(c.principal(), c.task(), r.invoice(), r.cents(), r.version()))) return "DENY approval";
            // A real service needs atomic authorization/version/approval consumption/ledger transaction.
            invoices.put(r.invoice(), new Invoice(invoice.tenant(), invoice.refundable() - r.cents(), invoice.version() + 1));
            effects++;
            return "REFUND " + r.cents() + " remaining=" + invoices.get(r.invoice()).refundable();
        }
    }
    static void expect(String got, String wanted) {
        if (!got.equals(wanted)) throw new AssertionError(got + " != " + wanted);
        System.out.println(got);
    }
    public static void main(String[] args) {
        var gate = new Gate();
        var summary = new Context("A", "alice", "summary-T1", Set.of(Op.READ, Op.REFUND), Set.of(Op.READ), Set.of("P7"));
        var refund = new Context("A", "alice", "refund-T2", Set.of(Op.READ, Op.REFUND), Set.of(Op.REFUND), Set.of("P7"));
        // This is a fixed adversarial model-output fixture, NOT a call to an LLM.
        var attack = new Request(Op.REFUND, "P7", 90, 4, true);
        if (!Gate.shape(attack)) throw new AssertionError();
        long naiveRemaining = 100 - attack.cents(); // unsafe in-memory dispatcher checks only shape
        if (naiveRemaining != 10) throw new AssertionError();
        System.out.println("shape=true; naive remaining=" + naiveRemaining);
        expect(gate.execute(summary, attack), "DENY task");
        expect(gate.execute(summary, new Request(Op.READ, "P8", 0, 2, true)), "DENY owner");
        expect(gate.execute(refund, attack), "DENY approval");
        gate.approvals.add(new Approval("alice", "refund-T2", "P7", 10, 4)); // human approved exact 10, not any refund
        expect(gate.execute(refund, attack), "DENY approval");
        var exact = new Request(Op.REFUND, "P7", 10, 4, false);
        expect(gate.execute(refund, exact), "REFUND 10 remaining=90");
        expect(gate.execute(refund, exact), "DENY stale version");
        if (gate.effects != 1 || !gate.approvals.isEmpty()) throw new AssertionError();
        System.out.println("effects=1; approvals remaining=0");
    }
}
