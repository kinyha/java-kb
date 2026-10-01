import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;

public class AdviceDemo {
    interface Gateway { void pay(); }
    static class BankGateway implements Gateway {
        public void pay() { System.out.println("target pay"); }
    }
    @Aspect @Order(1)
    static class Outer {
        @Around("bean(gateway) && execution(* pay(..))")
        Object around(ProceedingJoinPoint call) throws Throwable {
            System.out.println("outer enter");
            try { return call.proceed(); }
            finally { System.out.println("outer exit"); }
        }
    }
    @Aspect @Order(2)
    static class Inner {
        @Around("bean(gateway) && execution(* pay(..))")
        Object around(ProceedingJoinPoint call) throws Throwable {
            System.out.println("inner enter");
            try { return call.proceed(); }
            finally { System.out.println("inner exit"); }
        }
    }
    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy
    static class Config {
        @Bean Gateway gateway() { return new BankGateway(); }
        @Bean Outer outer() { return new Outer(); }
        @Bean Inner inner() { return new Inner(); }
    }
    public static void main(String[] args) {
        try (var ctx = new AnnotationConfigApplicationContext(Config.class)) {
            ctx.getBean(Gateway.class).pay();
        }
    }
}
