package kb.boot;

import java.util.Arrays;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

public class AutoConfigDemo {
    public interface BankGateway {
        String charge(String paymentId);
    }

    // Маркер изображает класс из SDK банка. Настоящей сети в этом опыте нет.
    public static class BankSdk {}

    @AutoConfiguration
    @ConditionalOnClass(BankSdk.class)
    @ConditionalOnProperty(prefix = "payments.bank", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public static class BankAutoConfiguration {
        @Bean
        @ConditionalOnMissingBean(BankGateway.class)
        BankGateway bankGateway() {
            return id -> "bank:" + id;
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class ManualConfiguration {
        @Bean
        BankGateway customGateway() { // Условие видит BankGateway в сигнатуре.
            return id -> "custom:" + id;
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class OpaqueConfiguration {
        @Bean
        Object customGateway() { // Объект тот же по интерфейсу, описание скрывает тип.
            return (BankGateway) id -> "opaque:" + id;
        }
    }

    public static class PaymentService {
        private final org.springframework.beans.factory.ObjectProvider<BankGateway> gateway;

        public PaymentService(org.springframework.beans.factory.ObjectProvider<BankGateway> gateway) {
            this.gateway = gateway; // Отложенный выбор позволяет увидеть и ноль, и два бина.
        }

        public String pay(String id) {
            BankGateway selected = gateway.getIfAvailable();
            return selected == null ? "unavailable:" + id : selected.charge(id);
        }
    }

    // Без component scan: варианты конфигурации добавляются явно, автоконфигурация — через imports.
    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(PaymentService.class)
    public static class Application {}

    private static ConfigurableApplicationContext start(Class<?> extra, String... args) {
        SpringApplication app = new SpringApplication(Application.class);
        if (extra != null) app.addPrimarySources(Arrays.asList(extra));
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        app.setDefaultProperties(Map.of("spring.main.register-shutdown-hook", "false"));
        return app.run(args);
    }

    private static void scenario(String name, Class<?> extra, String... args) {
        try (var context = start(extra, args)) {
            System.out.println(name + " gateways="
                    + Arrays.toString(context.getBeanNamesForType(BankGateway.class)));
            System.out.println(name + " result=" + context.getBean(PaymentService.class).pay("p-17"));
        }
    }

    private static void opaqueScenario() {
        try (var context = start(OpaqueConfiguration.class)) {
            System.out.println("opaque gateways="
                    + Arrays.toString(context.getBeanNamesForType(BankGateway.class)));
            try {
                System.out.println(context.getBean(PaymentService.class).pay("p-17"));
            } catch (org.springframework.beans.factory.NoUniqueBeanDefinitionException ex) {
                System.out.println("opaque result=" + ex.getClass().getSimpleName());
            }
        }
    }

    private static void reportScenario() {
        try (var context = start(ManualConfiguration.class)) {
            var report = ConditionEvaluationReport.get(context.getBeanFactory());
            String source = BankAutoConfiguration.class.getName() + "#bankGateway";
            var outcomes = report.getConditionAndOutcomesBySource().get(source);
            System.out.println("bankGateway fullMatch=" + outcomes.isFullMatch());
            outcomes.forEach(outcome -> System.out.println(outcome.getOutcome().getMessage()));
        }
    }

    public static void main(String[] args) {
        Logger.getLogger("").setLevel(Level.SEVERE); // Оставляем только вывод опыта.
        if (args.length > 0 && args[0].equals("report")) {
            reportScenario();
        } else if (args.length > 0 && args[0].equals("opaque")) {
            opaqueScenario();
        } else {
            scenario("default", null);
            scenario("manual", ManualConfiguration.class);
            scenario("disabled", null, "--payments.bank.enabled=false");
        }
    }
}
