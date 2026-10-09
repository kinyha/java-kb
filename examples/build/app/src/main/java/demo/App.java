package demo;

import demo.fixture.LegacyAdapter;
import demo.fixture.NewAdapter;

public final class App {
    public static void main(String[] args) {
        System.out.println("fee=" + Fee.minorUnits(101));
        System.out.println("legacy=" + LegacyAdapter.currency());
        // App compiles against NewAdapter; the JVM must also link its internal API call.
        System.out.println("new=" + NewAdapter.minorUnits());
    }
}
