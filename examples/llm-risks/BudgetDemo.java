public class BudgetDemo {
    static final class Budget {
        int calls;
        int units;
        // Illustrative cumulative reservation units, not provider token billing.
        boolean reserve(int estimatedMaximumUnits) {
            if (estimatedMaximumUnits <= 0) throw new IllegalArgumentException("positive reservation required");
            if (calls >= 3 || estimatedMaximumUnits > 10 - units) return false;
            calls++; units += estimatedMaximumUnits; // BEFORE dispatch; even a failed attempt consumes budget
            return true;
        }
    }
    public static void main(String[] args) {
        var budget = new Budget();
        int dispatched = 0;
        for (int turn = 1; turn <= 5; turn++) {
            if (!budget.reserve(3)) {
                System.out.println("turn=" + turn + " DENY budget; calls=" + budget.calls + " units=" + budget.units);
                break;
            }
            dispatched++;
            System.out.println("turn=" + turn + " dispatched; timeout; reserved units=" + budget.units);
            // Simulated timeout; the reservation is not reset by a retry or model asking to start over.
        }
        if (dispatched != 3 || budget.units != 9) throw new AssertionError();
        var oversized = new Budget();
        boolean accepted = oversized.reserve(11);
        if (accepted || oversized.calls != 0) throw new AssertionError();
        System.out.println("oversized accepted=" + accepted + " dispatched=" + oversized.calls);
    }
}
