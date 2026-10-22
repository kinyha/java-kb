import java.lang.reflect.Field;
import sun.misc.Unsafe;

// Так раньше выделяли память вне heap Netty, Kryo, старые Caffeine/Chronicle.
public class OffHeapBuffer {
    public static void main(String[] args) throws Exception {
        Field f = Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);                      // sun.misc открыт модулем jdk.unsupported: исключения нет
        Unsafe unsafe = (Unsafe) f.get(null);
        long address = unsafe.allocateMemory(64);   // метод доступа к памяти: forRemoval с JDK 23 (JEP 471)
        unsafe.putLong(address, 199_90L);
        System.out.println("off-heap value: " + unsafe.getLong(address));
        unsafe.freeMemory(address);
    }
}
