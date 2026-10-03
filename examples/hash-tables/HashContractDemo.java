import java.util.HashMap;

public class HashContractDemo {
    record PaymentKey(int merchantId, String requestId) {}

    static final class MutableKey {
        int id;
        MutableKey(int id) { this.id = id; }
        @Override public int hashCode() { return id; }
        @Override public boolean equals(Object other) {
            return other instanceof MutableKey key && id == key.id;
        }
        @Override public String toString() { return "id=" + id; }
    }

    public static void main(String[] args) {
        System.out.println("== hash and equality ==");
        String a = "Aa", b = "BB";
        System.out.println("hashes=" + a.hashCode() + "," + b.hashCode()
                + " equal=" + a.equals(b));
        var payments = new HashMap<String, String>();
        payments.put(a, "PAID");
        payments.put(b, "REFUNDED"); // Совпадение хешей не делает ключи равными.
        System.out.println("size=" + payments.size() + " Aa=" + payments.get(a)
                + " BB=" + payments.get(b));
        System.out.println("new Aa=" + payments.get(new String("Aa")));
        System.out.println("replace=" + payments.put(new String("Aa"), "SETTLED")
                + " size=" + payments.size());

        System.out.println("== bucket and sign ==");
        System.out.println("hashes=1,5 buckets=" + Math.floorMod(1, 4)
                + "," + Math.floorMod(5, 4));
        int hash = Integer.MIN_VALUE;
        System.out.println("abs%10=" + Math.abs(hash) % 10
                + " floorMod=" + Math.floorMod(hash, 10));

        System.out.println("== key boundary ==");
        var byRequest = new HashMap<String, String>();
        byRequest.put("A", "merchant-1");
        byRequest.put("A", "merchant-2"); // Неверная граница уникальности.
        var byMerchant = new HashMap<PaymentKey, String>();
        byMerchant.put(new PaymentKey(1, "A"), "merchant-1");
        byMerchant.put(new PaymentKey(2, "A"), "merchant-2");
        System.out.println("request-only=" + byRequest.size()
                + " merchant+request=" + byMerchant.size());
        System.out.println("merchant-1=" + byMerchant.get(new PaymentKey(1, "A")));

        System.out.println("== mutable key ==");
        var key = new MutableKey(1);
        var map = new HashMap<MutableKey, String>(16);
        map.put(key, "PAID");
        key.id = 33; // На JDK 21 оба хеша дают бакет 1 при ёмкости 16.
        System.out.println("bucket before=" + (1 & 15) + " after=" + (33 & 15));
        System.out.println("same object=" + map.get(key) + " size=" + map.size());
        System.out.println("iteration=" + map.keySet());
        key.id = 1; // Восстановленный хеш снова совпадает с сохранённым в узле.
        System.out.println("restored=" + map.get(key));
    }
}
