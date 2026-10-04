import java.util.*;

public class FlowDemo {
    record Item(int id, int created, int started, int finished) {}
    record Run(List<Item> items, int peakWip) {
        int lastFinish() { return items.getLast().finished(); }
        int lastStart() { return items.getLast().started(); }
        double meanCycle() {
            return items.stream().mapToInt(i -> i.finished() - i.started()).average().orElse(0);
        }
        double meanLead() {
            return items.stream().mapToInt(i -> i.finished() - i.created()).average().orElse(0);
        }
    }

    static Run simulate(int count, int limit) {
        List<Item> items = new ArrayList<>();
        int codeFree = 0, reviewFree = 0, peak = 0;
        for (int id = 0; id < count; id++) {
            int start = codeFree;
            while (true) {
                int active = 0, earliestFinish = Integer.MAX_VALUE;
                for (Item item : items) {
                    if (item.finished() > start) { // уже начатое, включая очередь проверки
                        active++;
                        earliestFinish = Math.min(earliestFinish, item.finished());
                    }
                }
                if (active < limit) {
                    peak = Math.max(peak, active + 1);
                    break;                        // есть место для начала следующей карточки
                }
                start = earliestFinish;            // pull: ждём выхода работы из системы
            }
            int codeDone = start + 1;               // один разработчик, один день на код
            int finish = Math.max(codeDone, reviewFree) + 3; // один проверяющий, три дня
            items.add(new Item(id, 0, start, finish)); // все заявки появились в день 0
            codeFree = codeDone;
            reviewFree = finish;
        }
        return new Run(List.copyOf(items), peak);
    }

    static void flowComparison() {
        Run unlimited = simulate(12, Integer.MAX_VALUE);
        Run limited = simulate(12, 2);
        System.out.println("== WIP changes starts, not review speed ==");
        System.out.printf(Locale.ROOT, "unlimited peak=%d finish=%d mean cycle=%.2f%n",
                unlimited.peakWip(), unlimited.lastFinish(), unlimited.meanCycle());
        System.out.printf(Locale.ROOT, "limit2 peak=%d finish=%d mean cycle=%.2f%n",
                limited.peakWip(), limited.lastFinish(), limited.meanCycle());
    }

    static void includeQueue() {
        Run unlimited = simulate(12, Integer.MAX_VALUE);
        Run limited = simulate(12, 2);
        System.out.println("== include waiting before start ==");
        System.out.printf(Locale.ROOT, "unlimited mean lead=%.2f last start=%d%n",
                unlimited.meanLead(), unlimited.lastStart());
        System.out.printf(Locale.ROOT, "limit2 mean lead=%.2f last start=%d%n",
                limited.meanLead(), limited.lastStart());
    }

    record Ticket(String id, int started, boolean blocked) {}

    static void blockedSnapshot() {
        int today = 10;
        List<Ticket> tickets = List.of(
                new Ticket("P1", 2, true),
                new Ticket("P2", 5, false),
                new Ticket("P3", 9, false));
        long hidden = tickets.stream().filter(t -> !t.blocked()).count();
        Ticket oldest = tickets.stream().min(Comparator.comparingInt(Ticket::started)).orElseThrow();
        System.out.println("== blocked is still started work ==");
        System.out.println("without blocked=" + hidden);
        System.out.println("actual WIP=" + tickets.size());
        System.out.println("oldest=" + oldest.id() + " age=" + (today - oldest.started()));
    }

    public static void main(String[] args) {
        flowComparison();
        includeQueue();
        blockedSnapshot();
    }
}
