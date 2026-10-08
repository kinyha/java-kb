import java.lang.reflect.Modifier;
import java.util.*;

public class RecordsDemo {
    record Receipt(String id, long minorUnits) {}
    record Normalized(String id) {
        Normalized {
            id = Objects.requireNonNull(id).strip().toUpperCase(Locale.ROOT);
            // Поля присвоятся после тела. Через accessor пока виден null.
            System.out.println("constructor: param=" + id + ", accessor=" + id());
            if (id.isEmpty()) throw new IllegalArgumentException("empty id");
        }
        Normalized() { this("fallback"); } // другой конструктор делегирует canonical
    }

    public static void main(String[] args) throws Exception {
        Receipt a = new Receipt("p1", 100), b = new Receipt("p1", 100);
        System.out.println("identity=" + (a == b) + ", equals=" + a.equals(b));
        System.out.println(a);
        Class<?> type = Receipt.class;
        System.out.println("record=" + type.isRecord() + ", final=" + Modifier.isFinal(type.getModifiers()));
        System.out.println("parent=" + type.getSuperclass().getName());
        for (var component : type.getRecordComponents()) {
            System.out.println(component.getName() + ": " + component.getType().getSimpleName()
                    + ", accessor=" + component.getAccessor().getName());
        }
        var field = type.getDeclaredField("minorUnits");
        field.setAccessible(true);
        try {
            field.setLong(a, 200); // final-поле record нельзя менять этим способом
            throw new AssertionError("field changed");
        } catch (IllegalAccessException expected) {
            System.out.println("reflection write: IllegalAccessException");
        }
        Normalized n = new Normalized(" p1 ");
        System.out.println("after constructor=" + n.id());
        try {
            new Normalized("  ");
            throw new AssertionError("invalid id accepted");
        } catch (IllegalArgumentException expected) {
            System.out.println("empty id rejected");
        }
    }
}
