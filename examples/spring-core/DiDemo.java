import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;

public class DiDemo {
    record Gateway(String name) {}
    static class Missing {}
    static class FieldClient {
        @Autowired Gateway gateway;
        FieldClient() { System.out.println("field during constructor: " + gateway); }
    }
    static class Client {
        final Gateway selected;
        final List<Gateway> all;
        final Optional<Missing> optional;
        final ObjectProvider<Missing> deferred;
        Client(@Qualifier("eu") Gateway selected, List<Gateway> all,
               Optional<Missing> optional, ObjectProvider<Missing> deferred) {
            this.selected=selected; this.all=all; this.optional=optional; this.deferred=deferred;
        }
    }
    @Configuration(proxyBeanMethods=false)
    @Import({Client.class, FieldClient.class})
    static class Config {
        @Bean @Qualifier("eu") @Order(1) Gateway euFast() { return new Gateway("eu-fast"); }
        @Bean @Qualifier("eu") @Primary @Order(2) Gateway euSafe() { return new Gateway("eu-safe"); }
        @Bean @Order(3) Gateway us() { return new Gateway("us"); }
        @Bean @Fallback @Order(4) Gateway fallback() { return new Gateway("fallback"); }
    }
    @Configuration(proxyBeanMethods=false)
    static class FallbackConfig {
        @Bean Gateway real() { return new Gateway("real"); }
        @Bean @Fallback Gateway reserve() { return new Gateway("reserve"); }
    }
    public static void main(String[] args) {
        try (var ctx = new AnnotationConfigApplicationContext(Config.class)) {
            var c = ctx.getBean(Client.class);
            System.out.println("selected: " + c.selected.name());
            System.out.println("field after creation: " + ctx.getBean(FieldClient.class).gateway.name());
            System.out.println("all: " + c.all.stream().map(Gateway::name).toList()); // Primary не сужает List.
            System.out.println("optional: " + c.optional.isPresent());
            System.out.println("available: " + (c.deferred.getIfAvailable() != null));
            try { c.deferred.getObject(); }
            catch (org.springframework.beans.factory.NoSuchBeanDefinitionException ex) {
                System.out.println("getObject: " + ex.getClass().getSimpleName());
            }
        }
        try (var ctx = new AnnotationConfigApplicationContext(FallbackConfig.class)) {
            System.out.println("non-fallback wins: " + ctx.getBean(Gateway.class).name());
        }
    }
}
