import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;

// Так устроены старые XStream, Kryo и самописные сериализаторы:
// обходят все поля объекта рефлексией, включая поля классов JDK.
public class LegacySerializer {
    record Payment(String id, BigDecimal amount) {}

    static String dump(Object o) throws IllegalAccessException {
        if (o == null || o instanceof String || o instanceof Integer || o instanceof Long) return String.valueOf(o);
        StringBuilder sb = new StringBuilder(o.getClass().getSimpleName()).append('{');
        for (Field f : o.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            f.setAccessible(true);                 // для полей BigDecimal это глубокая рефлексия в java.base
            sb.append(' ').append(f.getName()).append('=').append(dump(f.get(o)));
        }
        return sb.append(" }").toString();
    }

    public static void main(String[] args) throws Exception {
        Payment p = new Payment("pay-42", new BigDecimal("199.90"));
        Field intCompact = BigDecimal.class.getDeclaredField("intCompact");
        System.out.println("trySetAccessible: " + intCompact.trySetAccessible()); // false, если пакет не открыт
        System.out.println(dump(p));
    }
}
