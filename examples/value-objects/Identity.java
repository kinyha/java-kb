import java.lang.ref.WeakReference;
import java.util.*;

// JDK 28: java --enable-preview Identity.java
public class Identity {
    value record Money(long kopecks, String currency) { }

    static value class Ticket implements java.io.Serializable {   // value class, не record
        private final long id;
        Ticket(long id) { this.id = id; }
    }

    public static void main(String[] args) throws Exception {
        Money fee = new Money(150, "RUB");
        Object lock = fee;                                  // статический тип Object: javac не видит value-класс

        try { synchronized (lock) { } }
        catch (IdentityException e) { System.out.println("synchronized: " + e.getMessage()); }

        try { lock.wait(10); }
        catch (IllegalMonitorStateException e) { System.out.println("wait:         " + e.getClass().getSimpleName()); }

        try { new WeakReference<>(fee); }
        catch (IdentityException e) { System.out.println("WeakReference:" + " " + e.getMessage()); }

        Map<Object, String> seen = new IdentityHashMap<>();
        seen.put(new Money(150, "RUB"), "first");
        seen.put(new Money(150, "RUB"), "second");          // == и identityHashCode совпадают
        System.out.println("IdentityHashMap: " + seen);

        Integer x = new Integer(1996), y = new Integer(1996);   // deprecated, но компилируется
        System.out.println("new Integer == new Integer: " + (x == y));

        System.out.println("hasIdentity(fee)   = " + Objects.hasIdentity(fee));
        System.out.println("hasIdentity(\"RUB\") = " + Objects.hasIdentity("RUB"));

        var field = Money.class.getDeclaredField("kopecks");
        field.setAccessible(true);
        try { field.set(fee, 0L); }
        catch (IllegalAccessException e) { System.out.println("Field.set:    " + e.getMessage()); }

        try { new java.io.ObjectOutputStream(new java.io.ByteArrayOutputStream()).writeObject(new Ticket(7)); }
        catch (java.io.InvalidClassException e) { System.out.println("serialize:    " + e.getMessage()); }
    }
}
