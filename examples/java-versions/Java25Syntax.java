import module java.base; // imports public types from the module's exported packages

public class Java25Syntax {
    static class Named {
        static int constructed;
        final String name;
        Named(String name) { constructed++; this.name = name; }
    }
    static class Receipt extends Named {
        Receipt(String raw) {
            String normalized = raw.strip(); // Java 25: validate arguments before super
            if (normalized.isEmpty()) throw new IllegalArgumentException("empty");
            super(normalized); // constructor prologue did not read this or call an instance method
        }
    }
    void main() { // Java 25: instance main, launcher constructs Java25Syntax
        try {
            new Receipt("  ");
            throw new AssertionError("empty accepted");
        } catch (IllegalArgumentException expected) {
            if (Named.constructed != 0) throw new AssertionError();
            System.out.println("rejected before base=" + (Named.constructed == 0));
        }
        var receipt = new Receipt("  P7  ");
        System.out.println("name=" + receipt.name + "; base calls=" + Named.constructed);
        System.out.println("month=" + LocalDate.of(2025, 9, 16).getMonth()); // resolved by module import
    }
}
