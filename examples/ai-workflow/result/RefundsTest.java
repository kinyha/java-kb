public class RefundsTest {
    public static void main(String[] args) {
        // Criteria 1: Returns no more than balance (paid - fee - alreadyRefunded)
        long result = Refunds.refund(1000, 100, 200, 300);
        assert result == 300 : "Should return requested amount when within balance";

        result = Refunds.refund(1000, 100, 200, 500);
        assert result == 500 : "Should return requested amount when below balance";

        // Criteria 4: Exact refund for entire balance is allowed
        // paid=1000, fee=15, already=0, requested=985 → 985
        result = Refunds.refund(1000, 15, 0, 985);
        assert result == 985 : "Should allow exact refund for entire balance";

        // Balance = 1000 - 100 - 200 = 700
        // Request 600, should return 600
        result = Refunds.refund(1000, 100, 200, 600);
        assert result == 600 : "Should return requested when less than balance";

        // Criteria 2: If requested > balance, throw IllegalArgumentException with "exceeds"
        try {
            Refunds.refund(1000, 100, 200, 800); // balance = 700
            assert false : "Should throw IllegalArgumentException when requested > balance";
        } catch (IllegalArgumentException e) {
            assert e.getMessage().contains("exceeds") :
                "Exception message should contain 'exceeds', got: " + e.getMessage();
        }

        // Added by human review: one kopeck over the balance must fail too
        try {
            Refunds.refund(1000, 100, 200, 701); // balance = 700
            assert false : "Should throw when requested is one kopeck over balance";
        } catch (IllegalArgumentException e) {
            assert e.getMessage().contains("exceeds") : "got: " + e.getMessage();
        }

        // Criteria 3: requested <= 0 throws IllegalArgumentException with "positive"
        try {
            Refunds.refund(1000, 100, 200, 0);
            assert false : "Should throw IllegalArgumentException when requested == 0";
        } catch (IllegalArgumentException e) {
            assert e.getMessage().contains("positive") :
                "Exception message should contain 'positive', got: " + e.getMessage();
        }

        try {
            Refunds.refund(1000, 100, 200, -100);
            assert false : "Should throw IllegalArgumentException when requested < 0";
        } catch (IllegalArgumentException e) {
            assert e.getMessage().contains("positive") :
                "Exception message should contain 'positive', got: " + e.getMessage();
        }

        System.out.println("All tests passed!");
    }
}
