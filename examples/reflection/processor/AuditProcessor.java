package audit;

import java.util.Set;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.tools.Diagnostic;

// Обработчик аннотаций: работает внутри javac, видит исходные элементы и может остановить сборку.
@SupportedAnnotationTypes("audit.Audited")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class AuditProcessor extends AbstractProcessor {
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment env) {
        for (Element e : env.getElementsAnnotatedWith(Audited.class)) {
            String name = e.getEnclosingElement().getSimpleName() + "." + e.getSimpleName();
            if (!e.getModifiers().contains(Modifier.PUBLIC))
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "@Audited method must be public (proxies cannot intercept it): " + name, e);
            else
                processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "audited: " + name + " -> " + e.getAnnotation(Audited.class).value());
        }
        return true;
    }
}
