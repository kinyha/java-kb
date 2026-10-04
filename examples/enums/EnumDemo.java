import java.io.*;
import java.lang.reflect.Constructor;
import java.util.*;
import javax.tools.*;

// Запуск: java --add-opens java.base/java.util=ALL-UNNAMED EnumDemo.java   (JDK 21)
// --add-opens нужен только чтобы показать битовое поле внутри EnumSet.
public class EnumDemo {
    // Поля, конструктор и поведение, своё у каждой константы.
    enum PaymentStatus {
        CREATED(false) { PaymentStatus next() { return PENDING; } },
        PENDING(false) { PaymentStatus next() { return SUCCEEDED; } },
        SUCCEEDED(true) { PaymentStatus next() { throw new IllegalStateException("final"); } },
        FAILED(true) { PaymentStatus next() { throw new IllegalStateException("final"); } };

        private final boolean terminal;
        PaymentStatus(boolean terminal) { this.terminal = terminal; }
        boolean terminal() { return terminal; }
        abstract PaymentStatus next();
    }

    enum Channel { CARD, SBP, WALLET }
    enum ChannelV2 { CARD, SBP, CRYPTO, WALLET }               // новая версия: CRYPTO вставлен перед WALLET
    enum Big { E0,E1,E2,E3,E4,E5,E6,E7,E8,E9,E10,E11,E12,E13,E14,E15,E16,E17,E18,E19,E20,E21,E22,E23,E24,E25,E26,E27,E28,E29,E30,E31,
               E32,E33,E34,E35,E36,E37,E38,E39,E40,E41,E42,E43,E44,E45,E46,E47,E48,E49,E50,E51,E52,E53,E54,E55,E56,E57,E58,E59,E60,E61,E62,E63,E64 }

    // Singleton через enum: один экземпляр гарантирует JVM.
    enum Registry { INSTANCE; final Map<String, String> config = new HashMap<>(); }

    public static void main(String[] args) throws Exception {
        System.out.println("== what an enum compiles to");
        System.out.println("PaymentStatus superclass: " + PaymentStatus.class.getSuperclass().getSimpleName()
                + ", final: " + java.lang.reflect.Modifier.isFinal(PaymentStatus.class.getModifiers())
                + ", sealed: " + PaymentStatus.class.isSealed());
        System.out.println("Channel (no constant bodies) final: " + java.lang.reflect.Modifier.isFinal(Channel.class.getModifiers()));
        System.out.println("CREATED class: " + PaymentStatus.CREATED.getClass().getName()
                + ", getDeclaringClass: " + PaymentStatus.CREATED.getDeclaringClass().getSimpleName());
        System.out.println("values() == values(): " + (PaymentStatus.values() == PaymentStatus.values()) + " (a fresh copy each call)");
        System.out.println("CREATED.next() = " + PaymentStatus.CREATED.next() + ", SUCCEEDED.terminal() = " + PaymentStatus.SUCCEEDED.terminal());

        System.out.println("== ordinal stored in the database, then a constant is inserted");
        int stored = Channel.WALLET.ordinal();                 // в базе лежит число 2
        System.out.println("stored ordinal " + stored + " for " + Channel.WALLET + " reads back as " + ChannelV2.values()[stored]);
        System.out.println("stored name WALLET reads back as " + ChannelV2.valueOf("WALLET"));
        try { ChannelV2.valueOf("wallet"); } catch (IllegalArgumentException e) { System.out.println("valueOf(\"wallet\"): " + e.getMessage()); }

        System.out.println("== EnumSet and EnumMap");
        EnumSet<PaymentStatus> finals = EnumSet.of(PaymentStatus.SUCCEEDED, PaymentStatus.FAILED);
        System.out.println("EnumSet implementation: " + finals.getClass().getSimpleName() + ", contents " + finals);
        var bits = finals.getClass().getDeclaredField("elements");
        bits.setAccessible(true);
        System.out.println("bit vector: " + Long.toBinaryString((long) bits.get(finals)) + " (bit = ordinal)");
        System.out.println("EnumSet of 65 constants: " + EnumSet.allOf(Big.class).getClass().getSimpleName());
        EnumMap<Channel, Long> volume = new EnumMap<>(Channel.class);
        volume.put(Channel.WALLET, 3L); volume.put(Channel.CARD, 10L);
        System.out.println("EnumMap iterates in declaration order: " + volume);

        System.out.println("== switch over an enum after a constant is added");
        compile("""
            enum Channel { CARD, SBP, WALLET, CRYPTO }
            class Fees {
                static long expr(Channel c) { return switch (c) { case CARD -> 15; case SBP -> 0; case WALLET -> 10; }; }
                static long stmt(Channel c) { long f = 0; switch (c) { case CARD: f = 15; break; case SBP: f = 0; break; case WALLET: f = 10; break; } return f; }
            }""");

        System.out.println("== enum singleton vs reflection and serialization");
        Constructor<Registry> ctor = Registry.class.getDeclaredConstructor(String.class, int.class);
        ctor.setAccessible(true);
        try { ctor.newInstance("SECOND", 1); }
        catch (IllegalArgumentException e) { System.out.println("reflection: " + e.getMessage()); }
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) { out.writeObject(Registry.INSTANCE); }
        Object back;
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) { back = in.readObject(); }
        System.out.println("deserialized == INSTANCE: " + (back == Registry.INSTANCE));
    }

    // Компилирует исходник в памяти и печатает ошибки javac.
    static void compile(String src) {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        var diags = new DiagnosticCollector<JavaFileObject>();
        var file = new SimpleJavaFileObject(java.net.URI.create("string:///Fees.java"), JavaFileObject.Kind.SOURCE) {
            public CharSequence getCharContent(boolean ignore) { return src; }
        };
        var fm = javac.getStandardFileManager(diags, null, null);
        boolean ok = javac.getTask(new StringWriter(), fm, diags, List.of("-d", System.getProperty("java.io.tmpdir")), null, List.of(file)).call();
        for (var d : diags.getDiagnostics())
            System.out.println("   line " + d.getLineNumber() + ": " + d.getMessage(Locale.ROOT).lines().findFirst().orElse(""));
        System.out.println("   compiled: " + ok);
    }
}
