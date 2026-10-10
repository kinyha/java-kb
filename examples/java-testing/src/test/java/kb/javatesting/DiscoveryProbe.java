package kb.javatesting;

import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

public class DiscoveryProbe {
    public static void main(String[] args) {
        try {
            var launcher = LauncherFactory.create();
            var request = LauncherDiscoveryRequestBuilder.request().selectors(selectClass(FeePolicyTest.class)).build();
            launcher.discover(request); // actual engine lookup can be deferred until discovery
            System.out.println("launcher=" + launcher.getClass().getSimpleName());
        } catch (RuntimeException e) {
            System.out.println(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
