import java.util.*;

public class ClimbStairs {
    static long solve(int n) {
        long previous = 1, current = 1;
        for (int i = 2; i <= n; i++) {
            long next = Math.addExact(previous, current);
            previous = current;
            current = next;
        }
        return current;
    }
    public static void main(String[] args) {
        check(solve(0), 1L);
        check(solve(1), 1L);
        check(solve(2), 2L);
        check(solve(6), 13L);
        check(solve(12), 233L);
        check(solve(46), 2971215073L);
        check(solve(91), 7540113804746346429L);
    }

    static void check(Object got, Object want) {
        if (!Objects.equals(got,want)) throw new AssertionError("got=" + got + " want=" + want);
        System.out.println("OK got=" + got + " want=" + want);
    }
}
