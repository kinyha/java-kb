import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

// Запуск: java ShardingDemo.java   (JDK 21)
public class ShardingDemo {
    public static void main(String[] args) throws Exception {
        int keys = 1_000_000;
        System.out.println("== keys that move when growing from 4 to 5 shards");
        int movedMod = 0, movedRing = 0, movedVirtual = 0;
        TreeMap<Long, Integer> ring4 = ring(4, 200), ring5 = ring(5, 200);
        int[] slotOwner4 = slotOwners(4), slotOwner5 = rebalance(slotOwners(4), 5);
        for (int i = 0; i < keys; i++) {
            long h = hash("merchant-" + i);
            if (Math.floorMod(h, 4) != Math.floorMod(h, 5)) movedMod++;
            if (owner(ring4, h) != owner(ring5, h)) movedRing++;
            int slot = (int) Math.floorMod(h, 1024);
            if (slotOwner4[slot] != slotOwner5[slot]) movedVirtual++;
        }
        System.out.printf("   hash mod N:                 %5.1f%%%n", 100.0 * movedMod / keys);
        System.out.printf("   consistent hashing (ring):  %5.1f%%%n", 100.0 * movedRing / keys);
        System.out.printf("   1024 virtual shards:        %5.1f%%%n", 100.0 * movedVirtual / keys);

        System.out.println("== load per shard when one merchant makes 30% of payments (shard by merchant)");
        Map<Integer, Integer> load = new TreeMap<>();
        for (int p = 0; p < 1_000_000; p++) {
            String merchant = p % 10 < 3 ? "megastore" : "merchant-" + (p % 50_000);
            load.merge((int) Math.floorMod(hash(merchant), 4), 1, Integer::sum);
        }
        load.forEach((s, n) -> System.out.printf("   shard %d: %4.1f%%%n", s, n / 10_000.0));
    }

    static long hash(String key) throws Exception {
        byte[] d = MessageDigest.getInstance("MD5").digest(key.getBytes(StandardCharsets.UTF_8));
        long h = 0;
        for (int i = 0; i < 8; i++) h = (h << 8) | (d[i] & 0xff);
        return h;
    }

    // Consistent hashing: каждый шард — 200 точек на кольце; ключ идёт к ближайшей точке по часовой.
    static TreeMap<Long, Integer> ring(int shards, int pointsPerShard) throws Exception {
        TreeMap<Long, Integer> r = new TreeMap<>();
        for (int s = 0; s < shards; s++) for (int v = 0; v < pointsPerShard; v++) r.put(hash("shard-" + s + "#" + v), s);
        return r;
    }

    static int owner(TreeMap<Long, Integer> ring, long h) {
        var e = ring.ceilingEntry(h);
        return (e != null ? e : ring.firstEntry()).getValue();
    }

    // Виртуальные шарды: 1024 слота, таблица «слот → сервер».
    static int[] slotOwners(int shards) {
        int[] o = new int[1024];
        for (int s = 0; s < 1024; s++) o[s] = s % shards;
        return o;
    }

    // Новый сервер забирает у каждого старого поровну слотов; остальные слоты не двигаются.
    static int[] rebalance(int[] owners, int shards) {
        int[] o = owners.clone();
        int target = 1024 / shards;
        Map<Integer, Integer> taken = new HashMap<>();
        int moved = 0;
        for (int s = 0; s < 1024 && moved < target; s++) {
            int from = o[s];
            if (taken.getOrDefault(from, 0) < target / (shards - 1) + 1) {
                o[s] = shards - 1;
                taken.merge(from, 1, Integer::sum);
                moved++;
            }
        }
        return o;
    }
}
