public class ScopedValuesDemo {
    private static final ScopedValue<String> TENANT = ScopedValue.newInstance();
    static void observe(String label, String expected) {
        if (!TENANT.get().equals(expected)) throw new AssertionError();
        System.out.println(label + "=" + TENANT.get());
    }
    public static void main(String[] args) {
        ScopedValue.where(TENANT, "A").run(() -> {
            observe("outer", "A");
            try {
                ScopedValue.where(TENANT, "B").run(() -> {
                    observe("inner", "B");
                    throw new IllegalStateException("fixture");
                });
            } catch (IllegalStateException expected) {
                if (!expected.getMessage().equals("fixture")) throw expected;
            }
            observe("restored", "A"); // nested binding disappears even after an exception
            var child = Thread.startVirtualThread(() -> {
                if (TENANT.isBound()) throw new AssertionError("unexpected inheritance");
                System.out.println("plain child bound=" + TENANT.isBound());
            });
            try { child.join(); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new RuntimeException(ex); }
        });
        if (TENANT.isBound()) throw new AssertionError("binding leaked");
        System.out.println("after bound=" + TENANT.isBound());
    }
}
