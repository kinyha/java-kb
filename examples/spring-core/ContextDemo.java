import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class ContextDemo {
    record Gateway(String origin) {}

    static class PaymentService {
        final Gateway gateway;
        PaymentService(Gateway gateway) {
            this.gateway = gateway; // Имя параметра станет доступно Spring с -parameters.
        }
    }

    static void compare(AnnotationConfigApplicationContext parent, String localName) {
        try (var child = new AnnotationConfigApplicationContext()) {
            child.setParent(parent);
            child.registerBean(localName, Gateway.class, () -> new Gateway("child"));
            child.registerBean(PaymentService.class);
            try {
                child.refresh();
                System.out.println(localName + " -> " + child.getBean(PaymentService.class).gateway.origin());
            } catch (org.springframework.beans.factory.UnsatisfiedDependencyException ex) {
                // Для сравнения нужен корень ошибки, а не весь стек запуска.
                Throwable cause = ex;
                while (cause.getCause() != null) cause = cause.getCause();
                System.out.println(localName + " -> " + cause.getClass().getSimpleName());
            }
        }
    }

    public static void main(String[] args) {
        // Ожидаемую ошибку второго варианта печатаем сами, без служебного WARNING.
        Logger.getLogger("org.springframework").setLevel(Level.SEVERE);
        try (var parent = new AnnotationConfigApplicationContext()) {
            parent.registerBean("gateway", Gateway.class, () -> new Gateway("parent"));
            parent.refresh();
            compare(parent, "gateway");      // Одно имя: дочерний бин скрывает родительский.
            compare(parent, "localGateway"); // Два имени: выбор зависит от метаданных параметра.
        }
    }
}
