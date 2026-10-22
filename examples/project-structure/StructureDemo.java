import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

// Компилирует четыре раскладки одного платёжного сервиса вместе с RefundController,
// который лезет в хранилище платежей в обход сервиса. Печатает, пустил ли его javac.
// Второй прогон делает все типы public и повторяет то же самое.
public class StructureDemo {
    record Case(String variant, String shortcut, String target, boolean blockedAsWritten) {}

    static final List<Case> CASES = List.of(
            new Case("layer", "layer", "PaymentRepository", false),
            new Case("feature", "feature", "PaymentRepository", true),
            new Case("hex", "hex-port", "Payments (port)", false),
            new Case("hex", "hex-adapter", "JdbcPayments", true),
            new Case("component", "component", "PaymentRepository", true));

    public static void main(String[] args) throws IOException {
        Path base = Path.of(args.length > 0 ? args[0] : ".");
        Path out = Files.createTempDirectory("structure-demo");
        for (boolean allPublic : new boolean[]{false, true}) {
            System.out.println(allPublic ? "== everything public" : "== as written");
            for (Case c : CASES) {                                    // Пять обходов из схемы.
                List<Path> variant = sources(base.resolve("variants").resolve(c.variant()));
                // Сначала убеждаемся, что сам вариант собирается без нарушения.
                String clean = compile(variant, allPublic, out);
                if (clean != null) throw new AssertionError(c.variant() + " does not compile: " + clean);

                List<Path> withShortcut = new ArrayList<>(variant);  // Исходники раскладки…
                withShortcut.addAll(sources(base.resolve("shortcuts").resolve(c.shortcut()))); // …и RefundController.
                String error = compile(withShortcut, allPublic, out);  // Первая ошибка javac или null.
                boolean blocked = error != null;
                System.out.printf(Locale.ROOT, "%-10s RefundController -> %-18s %s%n",
                        c.variant(), c.target(), blocked ? "error: " + error : "compiles");
                boolean expected = !allPublic && c.blockedAsWritten();
                if (blocked != expected) throw new AssertionError("unexpected result for " + c);
            }
        }
    }

    static List<Path> sources(Path dir) {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // Возвращает текст первой ошибки компиляции или null, если всё собралось.
    static String compile(List<Path> files, boolean allPublic, Path out) throws IOException {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var fm = javac.getStandardFileManager(diagnostics, Locale.ROOT, null)) {
            fm.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(out));
            List<JavaFileObject> units = new ArrayList<>();
            for (Path file : files) units.add(source(file, allPublic));
            javac.getTask(null, fm, diagnostics, List.of("--release", "21"), null, units).call();
        }
        return diagnostics.getDiagnostics().stream()
                .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                .map(d -> d.getMessage(Locale.ROOT))
                .findFirst().orElse(null);
    }

    static JavaFileObject source(Path file, boolean allPublic) throws IOException {
        String code = Files.readString(file);
        if (allPublic) {
            // Типы без модификатора доступа становятся public, остальной код не меняется.
            code = code.replaceAll("(?m)^(final |abstract )?(class|interface|record|enum) ", "public $1$2 ");
        }
        String text = code;
        return new SimpleJavaFileObject(URI.create("string:///" + file.toString().replace('\\', '/')),
                JavaFileObject.Kind.SOURCE) {
            @Override public CharSequence getCharContent(boolean ignoreEncodingErrors) { return text; }
        };
    }
}
