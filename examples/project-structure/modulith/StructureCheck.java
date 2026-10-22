import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

// run.sh вызывает этот класс несколько раз с разными каталогами классов:
// broken — refunds читает payments.persistence, later — к нему добавился reports, fixed — исправлено.
public class StructureCheck {
    public static void main(String[] args) throws Exception {
        String mode = args[0], variant = args[1];
        switch (mode) {
            case "modulith" -> modulith(variant);
            case "freeze" -> freeze(variant, Path.of(args[2]));
            case "spring" -> spring(variant);
            default -> throw new IllegalArgumentException(mode);
        }
    }

    // Spring Modulith: прямые подпакеты kb.shop — модули, вложенные в модуль пакеты — его внутренности.
    static void modulith(String variant) {
        ApplicationModules modules = ApplicationModules.of("kb.shop");
        String names = modules.stream().map(ApplicationModule::getIdentifier)
                .map(Object::toString).sorted().collect(Collectors.joining(", "));
        System.out.println(variant + " modules: " + names);
        var violations = modules.detectViolations();       // verify() бросил бы это же исключением.
        if (!violations.hasViolations()) System.out.println(variant + " verify: PASS");
        // Одно нарушение на каждую зависимость (поле, параметр, вызов); группируем по первой строке.
        violations.getMessages().stream()
                .collect(Collectors.groupingBy(m -> m.lines().findFirst().orElseThrow(),
                        TreeMap::new, Collectors.counting()))
                .forEach((m, n) -> System.out.println(variant + " verify: FAIL x" + n + " " + m));
    }

    // ArchUnit с заморозкой: известные нарушения записаны в хранилище, падают только новые.
    static void freeze(String variant, Path classes) throws IOException {
        ArchRule rule = FreezingArchRule.freeze(noClasses()
                .that().resideOutsideOfPackage("kb.shop.payments..")
                .should().dependOnClassesThat().resideInAPackage("kb.shop.payments.persistence.."));
        try {
            rule.check(new ClassFileImporter().importPath(classes));
            System.out.println(variant + " frozen rule: PASS");
        } catch (AssertionError e) {
            // В отчёте ArchUnit много строк; печатаем только сами нарушения.
            e.getMessage().lines().filter(l -> l.contains("<kb.shop."))
                    .forEach(l -> System.out.println(variant + " frozen rule: FAIL " + l.trim()));
        }
        System.out.println(variant + " stored violations: " + stored());
    }

    // Сколько нарушений лежит в хранилище: всё, кроме файла со списком правил.
    static long stored() throws IOException {
        try (Stream<Path> files = Files.list(Path.of("target/archunit_store"))) {
            long count = 0;
            for (Path f : files.filter(f -> !f.endsWith("stored.rules")).toList()) {
                count += Files.readAllLines(f).stream().filter(l -> !l.isBlank()).count();
            }
            return count;
        }
    }

    // Обычный контекст Spring: находит package-private бины и внедряет их через конструктор.
    static void spring(String variant) throws Exception {
        try (var context = new AnnotationConfigApplicationContext("kb.shop")) {
            Class<?> controller = Class.forName("kb.shop.refunds.RefundController");
            Object bean = context.getBean(controller);
            Object result = controller.getMethod("refund", String.class).invoke(bean, "p-1");
            System.out.println(variant + " spring: " + result);
        }
    }
}
