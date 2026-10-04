import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;

// Запуск: java ReflectionDemo.java   (JDK 21, без зависимостей)
public class ReflectionDemo {
    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD) @interface Audited { String value(); }
    @Retention(RetentionPolicy.CLASS)   @Target(ElementType.METHOD) @interface Internal {}
    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.RECORD_COMPONENT) @interface NotBlank {}
    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.RECORD_COMPONENT) @interface Max { long value(); }

    record RefundRequest(@NotBlank String paymentId, @Max(100_000) long amount, String reason) {}

    interface RefundService {
        void refund(String paymentId, long amount);
        void refundAll(List<String> ids);
    }

    static class DefaultRefundService implements RefundService {
        @Audited("refund") @Internal
        public void refund(String paymentId, long amount) { System.out.println("   refund " + paymentId + " " + amount); }
        @Audited("refund-all")
        public void refundAll(List<String> ids) { for (String id : ids) refund(id, 100); }   // вызов через this, мимо прокси
    }

    public static void main(String[] args) throws Exception {
        System.out.println("== retention: which annotations reflection can see");
        Method m = DefaultRefundService.class.getMethod("refund", String.class, long.class);
        System.out.println("@Audited (RUNTIME) present: " + m.isAnnotationPresent(Audited.class) + ", value = " + m.getAnnotation(Audited.class).value());
        System.out.println("@Internal (CLASS) present:  " + m.isAnnotationPresent(Internal.class));

        System.out.println("== annotation-driven validation of a record");
        System.out.println(validate(new RefundRequest("p-1", 500, null)));
        System.out.println(validate(new RefundRequest(" ", 250_000, "dup")));

        System.out.println("== dynamic proxy: audit around calls (how Spring AOP works for interfaces)");
        RefundService target = new DefaultRefundService();
        RefundService proxy = (RefundService) Proxy.newProxyInstance(RefundService.class.getClassLoader(),
                new Class<?>[] { RefundService.class }, (p, method, a) -> {
                    Method impl = target.getClass().getMethod(method.getName(), method.getParameterTypes());
                    Audited audited = impl.getAnnotation(Audited.class);
                    if (audited != null) System.out.println("   audit: " + audited.value() + " " + Arrays.toString(a));
                    return method.invoke(target, a);
                });
        System.out.println("JDK proxy class: " + Proxy.isProxyClass(proxy.getClass()) + ", is DefaultRefundService: " + (proxy instanceof DefaultRefundService));
        proxy.refund("p-1", 500);
        System.out.println("-- refundAll calls refund() twice internally:");
        proxy.refundAll(List.of("p-2", "p-3"));

        System.out.println("== private access and modules");
        Field f = RefundRequest.class.getDeclaredField("amount");
        f.setAccessible(true);
        System.out.println("own private field via setAccessible: " + f.get(new RefundRequest("p-9", 42, "x")));
        try {
            Field value = String.class.getDeclaredField("value");
            value.setAccessible(true);
        } catch (InaccessibleObjectException e) {
            System.out.println("String.value: InaccessibleObjectException (java.base does not open java.lang)");
        }
    }

    // Минимальный валидатор: обходит компоненты record и читает их аннотации.
    static List<String> validate(Record r) throws Exception {
        List<String> errors = new ArrayList<>();
        for (RecordComponent c : r.getClass().getRecordComponents()) {
            Object v = c.getAccessor().invoke(r);
            if (c.isAnnotationPresent(NotBlank.class) && (v == null || v.toString().isBlank()))
                errors.add(c.getName() + " must not be blank");
            Max max = c.getAnnotation(Max.class);
            if (max != null && ((Number) v).longValue() > max.value())
                errors.add(c.getName() + " must be <= " + max.value());
        }
        return errors.isEmpty() ? List.of("valid") : errors;
    }
}
