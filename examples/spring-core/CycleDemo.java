import jakarta.annotation.PostConstruct;
import java.util.logging.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class CycleDemo {
    static class ConstructorA {
        ConstructorA(ConstructorB b) {}
    }
    static class ConstructorB {
        ConstructorB(ConstructorA a) {}
    }
    static class FieldA { @Autowired FieldB b; }
    static class FieldB {
        @Autowired FieldA a;
        @PostConstruct void init() {
            System.out.println("B init sees A.b: " + a.b); // A ещё ожидает готовый B.
        }
    }
    static void run(String label, boolean allow, Class<?> a, Class<?> b) {
        System.out.println(label);
        try (var ctx = new AnnotationConfigApplicationContext()) {
            ctx.getDefaultListableBeanFactory().setAllowCircularReferences(allow);
            ctx.register(a, b);
            try {
                ctx.refresh();
                var first = ctx.getBean(FieldA.class);
                System.out.println("connected: " + (first.b.a == first));
            } catch (org.springframework.beans.factory.BeanCreationException ex) {
                Throwable cause = ex;
                while (cause.getCause() != null) cause = cause.getCause();
                System.out.println(cause.getClass().getSimpleName());
            }
        }
    }
    public static void main(String[] args) {
        Logger.getLogger("org.springframework").setLevel(Level.SEVERE);
        run("constructor cycle:", true, ConstructorA.class, ConstructorB.class);
        run("field cycle allowed:", true, FieldA.class, FieldB.class);
        run("field cycle forbidden:", false, FieldA.class, FieldB.class);
    }
}
