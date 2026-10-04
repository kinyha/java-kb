import java.util.*;
public class FirstRepeatDemo {
    static int baseline(String[] ids) {
        for (int j = 0; j < ids.length; j++) { // сначала минимизируем индекс повтора
            for (int i = 0; i < j; i++) {
                if (ids[i].equals(ids[j])) return j;
            }
        }
        return -1;
    }
    static int wrongOrder(String[] ids) {
        for (int i = 0; i < ids.length; i++) { // этот порядок минимизирует первый индекс
            for (int j = i + 1; j < ids.length; j++) {
                if (ids[i].equals(ids[j])) return j;
            }
        }
        return -1;
    }
    static int firstRepeat(String[] ids) {
        Set<String> seen = new HashSet<>();
        for (int j = 0; j < ids.length; j++) {
            if (!seen.add(ids[j])) return j; // add=false: такой ID был в префиксе
        }
        return -1;
    }
    static int wrongWrite(String[] ids) {
        Set<String> seen = new HashSet<>();
        for (int j = 0; j < ids.length; j++) {
            seen.add(ids[j]);                   // текущий элемент уже в памяти
            if (seen.contains(ids[j])) return j; // проверка всегда проходит
        }
        return -1;
    }
    record Work(long comparisons, int inserts) {}
    static Work uniqueWork(int n) {
        String[] ids = new String[n];
        for (int i = 0; i < n; i++) ids[i] = "p" + i;
        long comparisons = 0;
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < j; i++) {
                comparisons++;                // считаем сравнения, а не миллисекунды
                if (ids[i].equals(ids[j])) throw new AssertionError("unique input");
            }
        }
        Set<String> seen = new HashSet<>();
        int inserts = 0;
        for (String id : ids) {
            inserts++;
            if (!seen.add(id)) throw new AssertionError("unique input");
        }
        return new Work(comparisons, inserts);
    }
    public static void main(String[] args) {
        System.out.println("== first means different things ==");
        String[] ids = {"A", "B", "B", "A"};
        System.out.println("baseline=" + baseline(ids));
        System.out.println("first original=" + wrongOrder(ids));
        System.out.println("== equal content, different objects ==");
        String[] equal = {new String("p7"), new String("p7")};
        System.out.println("same reference=" + (equal[0] == equal[1]));
        System.out.println("repeat=" + firstRepeat(equal));
        System.out.println("empty=" + firstRepeat(new String[0]));
        System.out.println("== update changes the question ==");
        System.out.println("write then contains=" + wrongWrite(ids));
        System.out.println("add result=" + firstRepeat(ids));
        System.out.println("== count the repeated work ==");
        Work w = uniqueWork(1000);
        System.out.println("comparisons=" + w.comparisons());
        System.out.println("set inserts=" + w.inserts());
    }
}
