// Старый плагинный движок включал Security Manager, чтобы ограничить скрипты.
public class SandboxProbe {
    public static void main(String[] args) {
        System.out.println("security manager: " + System.getSecurityManager());
        System.setSecurityManager(new SecurityManager());   // JDK 17: deprecated forRemoval (JEP 411)
        System.out.println("sandbox enabled: " + (System.getSecurityManager() != null));
    }
}
