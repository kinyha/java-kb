package pay;

public class JavaCheckout {
    // Java не знает, что owner у Kotlin-метода не-null: компилятор javac пропустит null.
    public static String printReceipt(Receipts receipts) {
        String owner = new LegacyAccounts().findOwner("closed-7");
        return receipts.line(owner, 1_500);
    }
}
