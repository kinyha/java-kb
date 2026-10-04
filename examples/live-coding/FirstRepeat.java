import java.util.*;
public class FirstRepeat {

    static int solve(String[] ids) {
        Set<String> seen = new HashSet<>();
        for (int j = 0; j < ids.length; j++) {
            if (!seen.add(ids[j])) return j; // add=false: такой ID был в префиксе
        }
        return -1;
    }
    static void check(String name, Object got, Object want) {
        if (!Objects.equals(got, want)) throw new AssertionError(name + ": " + got + " != " + want);
        System.out.println(name + " OK");
    }
    public static void main(String[] args) {
        check("empty", solve(new String[0]), -1);
        check("single", solve(new String[]{"A"}), -1);
        check("none", solve(new String[]{"A", "B", "C"}), -1);
        check("repeat", solve(new String[]{"A", "B", "A"}), 2);
        check("first arrival", solve(new String[]{"A", "B", "B", "A"}), 2);
        check("same content", solve(new String[]{new String("A"), new String("A")}), 1);
        check("all same", solve(new String[]{"A", "A", "A"}), 1);
        String[] ids = {"C", "A", "C"};
        String[] before = ids.clone();
        solve(ids);
        check("input unchanged", Arrays.equals(ids, before), true);
    }
}
