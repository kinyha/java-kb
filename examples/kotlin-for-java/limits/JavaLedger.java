package pay;

import java.util.List;

// Java-код получает «read-only» список из Kotlin и не знает об этом.
public class JavaLedger {
    public static String tryAdd(List<Long> fees) {
        try { fees.add(99L); return "add ok " + fees; }
        catch (UnsupportedOperationException e) { return "add failed: " + e.getClass().getSimpleName(); }
    }
    public static String trySet(List<Long> fees) {
        try { fees.set(0, 0L); return "set ok " + fees; }
        catch (UnsupportedOperationException e) { return "set failed: " + e.getClass().getSimpleName(); }
    }
}
