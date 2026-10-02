import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import com.tngtech.archunit.library.metrics.ArchitectureMetrics;
import com.tngtech.archunit.library.metrics.MetricsComponents;

import java.nio.file.Path;
import java.util.Locale;
import java.util.TreeSet;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

public class ModuleDemo {
    private static final String BASE = "kb.modules.";
    private static final ArchRule NO_CYCLES = slices().matching(BASE + "(*)..")
            .should().beFreeOfCycles();
    private static final ArchRule NO_INTERNAL_ACCESS = noClasses()
            .that().resideOutsideOfPackage(BASE + "payments..")
            .should().dependOnClassesThat().resideInAPackage(BASE + "payments.internal..");

    public static void main(String[] args) {
        if (args.length != 1) throw new IllegalArgumentException("Usage: ModuleDemo target-dir");
        Path target = Path.of(args[0]);
        verify(target, "broken", true, true);
        verify(target, "port", false, true);
        JavaClasses fixed = verify(target, "fixed", false, false);

        // Компонент включает корневой пакет и вложенные пакеты этой функции.
        var components = MetricsComponents.fromPackages(fixed.getPackage("kb.modules").getSubpackages());
        var metrics = ArchitectureMetrics.componentDependencyMetrics(components);
        for (String module : new String[]{"payments", "bank", "notifications"}) {
            String id = BASE + module;
            System.out.printf(Locale.ROOT, "%s Ca=%d Ce=%d I=%.2f A=%.2f D=%.2f%n", module,
                    metrics.getAfferentCoupling(id), metrics.getEfferentCoupling(id),
                    metrics.getInstability(id), metrics.getAbstractness(id),
                    metrics.getNormalizedDistanceFromMainSequence(id));
        }
    }

    private static JavaClasses verify(Path target, String variant, boolean cycle, boolean leak) {
        JavaClasses classes = new ClassFileImporter().importPath(target.resolve(variant));
        if (classes.size() == 0) throw new IllegalStateException("No imported classes: " + variant);
        var edges = new TreeSet<String>();
        for (JavaClass source : classes) {
            String from = module(source);
            for (var dependency : source.getDirectDependenciesFromSelf()) {
                String to = module(dependency.getTargetClass());
                if (!to.isEmpty() && !from.equals(to)) edges.add(from + "->" + to);
            }
        }
        System.out.println(variant + " edges=" + String.join(",", edges));
        report(variant, "cycles", NO_CYCLES.evaluate(classes), cycle);
        report(variant, "internal access", NO_INTERNAL_ACCESS.evaluate(classes), leak);
        return classes;
    }

    private static String module(JavaClass type) {
        String name = type.getPackageName();
        if (!name.startsWith(BASE)) return "";
        return name.substring(BASE.length()).split("\\.")[0];
    }

    private static void report(String variant, String name, EvaluationResult result, boolean expectedFailure) {
        boolean failed = result.hasViolation();
        System.out.println(variant + " " + name + "=" + (failed ? "FAIL" : "PASS"));
        if (failed != expectedFailure) throw new AssertionError(result.getFailureReport().toString());
    }
}
