import java.util.Objects;

public class ProbingDemo {
    static final byte EMPTY = 0, USED = 1, DELETED = 2;

    static final class Table {
        final int[] keys = new int[8];
        final String[] values = new String[8];
        final byte[] states = new byte[8];
        int size;
        int deleted;
        int probes;

        String get(int key) {
            probes = 0;
            int home = Math.floorMod(key, keys.length);
            for (int step = 0; step < keys.length; step++) {
                int index = (home + step) % keys.length;
                probes++;
                if (states[index] == EMPTY) return null; // Здесь поиск заканчивается.
                if (states[index] == USED && keys[index] == key) return values[index];
                // DELETED сохраняет путь к более поздним записям.
            }
            return null; // Полный круг: ключа нет даже при отсутствии EMPTY.
        }

        String put(int key, String value) {
            Objects.requireNonNull(value);
            int home = Math.floorMod(key, keys.length);
            int firstDeleted = -1;
            for (int step = 0; step < keys.length; step++) {
                int index = (home + step) % keys.length;
                if (states[index] == USED && keys[index] == key) {
                    String old = values[index];
                    values[index] = value; // Существующий ключ мог стоять после DELETED.
                    return old;
                }
                if (states[index] == DELETED && firstDeleted == -1) firstDeleted = index;
                if (states[index] == EMPTY) {
                    insert(firstDeleted == -1 ? index : firstDeleted, key, value);
                    return null;
                }
            }
            if (firstDeleted != -1) {
                insert(firstDeleted, key, value);
                return null;
            }
            throw new IllegalStateException("table is full");
        }

        void insert(int index, int key, String value) {
            if (states[index] == DELETED) deleted--;
            states[index] = USED;
            keys[index] = key;
            values[index] = value;
            size++;
        }

        String remove(int key) {
            int home = Math.floorMod(key, keys.length);
            for (int step = 0; step < keys.length; step++) {
                int index = (home + step) % keys.length;
                if (states[index] == EMPTY) return null;
                if (states[index] == USED && keys[index] == key) {
                    String old = values[index];
                    states[index] = DELETED; // EMPTY здесь скрыл бы следующие ключи.
                    values[index] = null;
                    size--;
                    deleted++;
                    return old;
                }
            }
            return null;
        }

        String snapshot() {
            var result = new StringBuilder("n=" + size + " deleted=" + deleted + " slots=");
            for (int i = 0; i < keys.length; i++) {
                if (i > 0) result.append(" | ");
                result.append(i).append(':');
                if (states[i] == EMPTY) result.append('_');
                else if (states[i] == DELETED) result.append('D');
                else result.append(keys[i]);
            }
            return result.toString();
        }
    }

    static Table threePayments() {
        var table = new Table();
        table.put(1, "PAID");
        table.put(9, "PENDING");
        table.put(17, "REFUNDED");
        return table;
    }

    public static void main(String[] args) {
        System.out.println("== probing ==");
        var table = threePayments();
        System.out.println(table.snapshot());
        System.out.println("get 17=" + table.get(17) + " probes=" + table.probes);

        System.out.println("== broken deletion ==");
        var broken = threePayments();
        broken.states[2] = EMPTY; // Намеренная ошибка: разрываем маршрут через слот 2.
        broken.values[2] = null;
        broken.size--;
        System.out.println(broken.snapshot());
        System.out.println("get 17=" + broken.get(17) + " probes=" + broken.probes);

        System.out.println("== tombstone ==");
        table.remove(9);
        System.out.println(table.snapshot());
        System.out.println("get 17=" + table.get(17) + " probes=" + table.probes);
        System.out.println("replace 17=" + table.put(17, "SETTLED")
                + " n=" + table.size + " deleted=" + table.deleted);
        table.put(25, "NEW"); // Слот 2 переиспользуем после проверки оставшегося пути.
        System.out.println(table.snapshot());
        System.out.println("get 17=" + table.get(17) + " probes=" + table.probes);

        System.out.println("== occupancy ==");
        var crowded = new Table();
        for (int i = 0; i < 7; i++) crowded.put(i, "PAID");
        System.out.println(crowded.snapshot());
        System.out.println("get 8=" + crowded.get(8) + " probes=" + crowded.probes);
    }
}
