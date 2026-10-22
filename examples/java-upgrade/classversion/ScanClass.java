import java.nio.file.Files;
import java.nio.file.Path;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

// Так classpath-сканеры (Spring, Hibernate), агенты APM и Mockito читают байткод: через ASM, а не через JVM.
public class ScanClass {
    public static void main(String[] args) throws Exception {
        // Файл с диска или класс самой JDK: агент APM инструментирует и их.
        byte[] bytes = args[0].endsWith(".class") && Files.exists(Path.of(args[0]))
                ? Files.readAllBytes(Path.of(args[0]))
                : ClassLoader.getSystemResourceAsStream(args[0]).readAllBytes();
        int major = ((bytes[6] & 0xFF) << 8) | (bytes[7] & 0xFF);   // байты 6–7 class-файла: major version
        System.out.println("class file major version: " + major);
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(int version, int access, String name, String sig, String sup, String[] ifs) {
                System.out.println("ASM parsed: " + name);
            }
        }, 0);
    }
}
