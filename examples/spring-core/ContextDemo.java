import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.beans.factory.support.RootBeanDefinition;

public class ContextDemo {
    static class Gateway {}
    static class PaymentService {
        final Gateway gateway;
        PaymentService(Gateway gateway) { this.gateway = gateway; }
    }
    static class Report {
        Report() { System.out.println("Report created"); }
    }
    public static void main(String[] args) {
        try (var parent = new AnnotationConfigApplicationContext()) {
            parent.registerBean("gateway", Gateway.class);
            parent.registerBean("bankName", String.class, () -> "Parent bank");
            parent.refresh();
            try (var child = new AnnotationConfigApplicationContext()) {
                child.setParent(parent);
                child.registerBean(PaymentService.class);
                child.registerBean("gateway", Gateway.class); // То же имя скрывает bean родителя.
                var definition = new RootBeanDefinition(Report.class);
                definition.setLazyInit(true);
                child.registerBeanDefinition("report", definition);
                System.out.println("before refresh: " + child.containsBeanDefinition("report"));
                child.refresh();
                System.out.println("singleton exists: " + child.getBeanFactory().containsSingleton("report"));
                System.out.println("service uses local: " +
                    (child.getBean(PaymentService.class).gateway == child.getBean("gateway")));
                System.out.println("inherited name: " + child.getBean("bankName"));
                System.out.println("parent sees child: " + parent.containsBean("report"));
                Object first = child.getBean("report"); // Lazy bean создаётся на первом запросе.
                System.out.println("same report: " + (first == child.getBean("report")));
                System.out.println("manual is managed: " + (new Gateway() == child.getBean("gateway")));
            }
        }
    }
}
