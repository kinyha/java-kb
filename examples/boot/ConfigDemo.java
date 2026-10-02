package kb.boot;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.validation.FieldError;
import org.springframework.validation.annotation.Validated;

public class ConfigDemo {
    @Validated
    @ConfigurationProperties("payments.bank")
    public record PaymentSettings(@NotNull URI url, @NotNull Duration timeout,
                                  @Min(1) int maxAttempts, List<String> channels,
                                  Map<String, Integer> rules) {}

    public static class ScalarValues {
        @Value("${payments.bank.max-attempts:-1}")
        int canonical; // Каноническое имя увидит kebab-case из YAML.

        @Value("${payments.bank.maxAttempts:-1}")
        int camel; // Эта запись не найдёт max-attempts в YAML.
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PaymentSettings.class)
    @Import(ScalarValues.class)
    public static class Application {}

    private static String winner(ConfigurableEnvironment environment) {
        var name = ConfigurationPropertyName.of("payments.bank.timeout");
        for (var source : ConfigurationPropertySources.get(environment)) {
            var property = source.getConfigurationProperty(name);
            if (property != null) {
                // Origin сохраняет файл и строку либо источник аргумента/переменной.
                return String.valueOf(property.getOrigin());
            }
        }
        return "missing";
    }

    private static void failure(RuntimeException failure) {
        System.out.println("startup=" + failure.getClass().getSimpleName());
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof BindValidationException validation) {
                System.out.println("binding=validation");
                validation.getValidationErrors().getAllErrors().stream()
                        .map(error -> error instanceof FieldError field
                                ? field.getField() + ": " + field.getDefaultMessage()
                                : error.getDefaultMessage())
                        .sorted().forEach(System.out::println);
                return;
            }
            if (cause instanceof ConversionFailedException) {
                System.out.println("binding=conversion");
                return;
            }
        }
    }

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("").setLevel(java.util.logging.Level.SEVERE);
        boolean snapshot = args.length > 0 && args[0].equals("snapshot");
        String[] options = snapshot ? Arrays.copyOfRange(args, 1, args.length) : args;
        SpringApplication app = new SpringApplication(Application.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        app.setDefaultProperties(Map.of(
                "spring.config.location", "classpath:/config-demo/",
                "logging.level.root", "OFF",
                "spring.main.register-shutdown-hook", "false"));

        try (var context = app.run(options)) {
            var settings = context.getBean(PaymentSettings.class);
            var environment = context.getEnvironment();
            if (snapshot) {
                System.out.println("before=" + settings.timeout().toMillis() + "ms");
                environment.getPropertySources().addFirst(new MapPropertySource("live-change",
                        Map.of("payments.bank.timeout", "99ms")));
                System.out.println("environment=" + environment.getProperty("payments.bank.timeout"));
                System.out.println("after=" + settings.timeout().toMillis() + "ms");
                return;
            }
            var values = context.getBean(ScalarValues.class);
            System.out.println("profiles=" + Arrays.toString(environment.getActiveProfiles()));
            System.out.println("url=" + settings.url());
            System.out.println("timeout=" + settings.timeout().toMillis() + "ms");
            System.out.println("maxAttempts=" + settings.maxAttempts());
            System.out.println("channels=" + settings.channels());
            System.out.println("rules=" + new TreeMap<>(settings.rules()));
            System.out.println("value canonical=" + values.canonical + ", camel=" + values.camel);
            System.out.println("origin=" + winner(environment));
        } catch (RuntimeException ex) {
            failure(ex); // Ошибки опыта перехвачены; приложение с плохой настройкой не стартовало.
        }
    }
}
