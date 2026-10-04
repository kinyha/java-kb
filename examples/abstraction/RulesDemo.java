import java.io.StringWriter;
import java.net.URI;
import java.util.*;
import javax.tools.*;

// Запуск: java RulesDemo.java   (JDK 21, без зависимостей)
// Компилирует фрагменты в памяти и печатает, что сказал javac: так видно правила default-методов.
public class RulesDemo {
    public static void main(String[] args) {
        compile("two defaults with the same signature", """
            interface Audited  { default String tag() { return "audit"; } }
            interface Traced   { default String tag() { return "trace"; } }
            class Refunds implements Audited, Traced { }
            """);
        compile("resolved with X.super.m()", """
            interface Audited  { default String tag() { return "audit"; } }
            interface Traced   { default String tag() { return "trace"; } }
            class Refunds implements Audited, Traced {
                public String tag() { return Audited.super.tag() + "+" + Traced.super.tag(); }
            }
            """);
        compile("default method cannot override Object.equals", """
            interface Identified { String id(); default boolean equals(Object o) { return false; } }
            """);
        compile("interface fields are constants", """
            interface Limits { long MAX = 100_000; }
            class Use { void f() { Limits.MAX = 5; } }
            """);
        compile("static interface methods are not inherited", """
            interface Money { static long cents(long rub) { return rub * 100; } }
            class Rub implements Money { long f() { return Rub.cents(5); } }
            """);
        compile("abstract class: state and constructor", """
            abstract class Gateway {
                private final String name; private int calls;
                protected Gateway(String name) { this.name = name; }
                public final String pay(long amount) { calls++; return name + ":" + doPay(amount); }
                protected abstract String doPay(long amount);
            }
            class Sbp extends Gateway { Sbp() { super("sbp"); } protected String doPay(long a) { return "ok " + a; } }
            """);
    }

    static void compile(String title, String src) {
        System.out.println("== " + title);
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        var diags = new DiagnosticCollector<JavaFileObject>();
        var file = new SimpleJavaFileObject(URI.create("string:///Snippet.java"), JavaFileObject.Kind.SOURCE) {
            public CharSequence getCharContent(boolean ignore) { return src; }
        };
        var out = System.getProperty("java.io.tmpdir") + "/rules-demo";
        new java.io.File(out).mkdirs();
        boolean ok = javac.getTask(new StringWriter(), javac.getStandardFileManager(diags, null, null), diags,
                List.of("-d", out), null, List.of(file)).call();
        for (var d : diags.getDiagnostics())
            System.out.println("   line " + d.getLineNumber() + ": " + String.join(" ", d.getMessage(Locale.ROOT).lines().map(String::strip).toList()));
        System.out.println("   compiled: " + ok);
    }
}
