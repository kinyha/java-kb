import java.util.logging.*;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;

public class ProxyDemo {
    public interface Gateway {
        void pay();
        void audit();
        String owner();
    }
    public static class BankGateway implements Gateway {
        final String bank;
        public BankGateway() { bank = "bank"; }
        public void pay() {
            System.out.println("target pay");
            audit(); // Этот вызов идёт через this исходного объекта.
        }
        public void audit() { System.out.println("target audit"); }
        public final String owner() { return bank; }
    }
    static void run(boolean classProxy) {
        var target = new BankGateway();
        var factory = new ProxyFactory(target);
        factory.setInterfaces(Gateway.class);
        factory.setProxyTargetClass(classProxy);
        factory.addAdvice((MethodInterceptor) invocation -> {
            String method = invocation.getMethod().getName();
            System.out.println("advice > " + method);
            try { return invocation.proceed(); }
            finally { System.out.println("advice < " + method); }
        });
        var proxy = (Gateway) factory.getProxy();
        System.out.println(classProxy ? "CGLIB:" : "JDK:");
        System.out.println("class proxy: " + AopUtils.isCglibProxy(proxy));
        proxy.pay();
        proxy.audit(); // Здесь вызов приходит снаружи через прокси.
        System.out.println("owner: " + proxy.owner()); // В CGLIB метод final.
    }
    public static void main(String[] args) {
        // Предупреждение о final-методе ожидается; результат печатаем сами.
        Logger.getLogger("org.springframework").setLevel(Level.SEVERE);
        run(false);
        run(true);
    }
}
