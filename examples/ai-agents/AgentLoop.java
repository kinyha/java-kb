import java.nio.file.*;
import java.util.*;
import java.util.function.Function;

// Запуск: java AgentLoop.java   (JDK 21, без зависимостей и без сети)
// Форма цикла агента. «Модель» здесь заскриптована: она повторяет решения настоящего прогона
// из recorded/run2.jsonl, чтобы показать устройство цикла, а не качество модели.
public class AgentLoop {
    sealed interface Block permits Text, ToolUse, ToolResult {}
    record Text(String text) implements Block {}
    record ToolUse(String id, String tool, String input) implements Block {}
    record ToolResult(String id, boolean isError, String content) implements Block {}
    record Message(String role, List<Block> content) {}

    interface Model { Message next(List<Message> history); }               // один вызов API
    enum Decision { ALLOW, DENY }

    // Гейт прав: решает не модель, а код вокруг неё.
    static Decision gate(ToolUse call) {
        if (call.tool().equals("Read") || call.tool().equals("Grep")) return Decision.ALLOW;
        if (call.tool().equals("Bash") && call.input().matches("(ls|find|cat) .*")) return Decision.ALLOW;
        return Decision.DENY;                                               // в -p режиме спросить некого
    }

    static final Map<String, Function<String, String>> TOOLS = Map.of(
            "Read", path -> { try { return Files.readString(Path.of(path)); } catch (Exception e) { return "error: " + e.getMessage(); } },
            "Bash", cmd -> "(sandboxed) would run: " + cmd);

    public static void main(String[] args) {
        Model scripted = new Model() {                                       // решения из настоящего прогона
            int call = 0;
            public Message next(List<Message> h) {
                call++;
                return switch (call) {
                    case 1 -> new Message("assistant", List.of(new Text("I'll compile the project."), new ToolUse("t1", "Bash", "find . -name *.java")));
                    case 2 -> new Message("assistant", List.of(new ToolUse("t2", "Bash", "mkdir -p /tmp/out && javac -d /tmp/out src/pay/*.java")));
                    case 3 -> new Message("assistant", List.of(new ToolUse("t3", "Bash", "javac -d /tmp/out src/pay/*.java")));
                    default -> new Message("assistant", List.of(new Text("Ошибок компиляции нет.")));
                };
            }
        };

        List<Message> history = new ArrayList<>();
        history.add(new Message("user", List.of(new Text("Скомпилируй проект и скажи, есть ли ошибки."))));
        int maxTurns = 6, evidence = 0;
        for (int turn = 1; turn <= maxTurns; turn++) {
            Message reply = scripted.next(history);                          // модель видит ВСЮ историю
            history.add(reply);
            List<ToolUse> calls = reply.content().stream().filter(b -> b instanceof ToolUse).map(b -> (ToolUse) b).toList();
            System.out.printf("turn %d: history %d messages, ~%d chars sent%n", turn, history.size() - 1, size(history) - size(List.of(reply)));
            reply.content().stream().filter(b -> b instanceof Text).forEach(b -> System.out.println("   text: " + ((Text) b).text()));
            if (calls.isEmpty()) {                                            // stop_reason = end_turn
                System.out.println("   stop: no tool calls -> final answer");
                System.out.println("evidence from successful tool runs: " + evidence + (evidence == 0 ? "  <- the answer is a claim, not a result" : ""));
                return;
            }
            List<Block> results = new ArrayList<>();
            for (ToolUse c : calls) {                                         // stop_reason = tool_use
                Decision d = gate(c);
                String out = d == Decision.ALLOW ? TOOLS.get(c.tool()).apply(c.input()) : "This command requires approval";
                if (d == Decision.ALLOW && c.input().startsWith("javac")) evidence++;
                System.out.println("   -> " + c.tool() + " " + c.input() + "  [" + d + "]");
                results.add(new ToolResult(c.id(), d == Decision.DENY, out));
            }
            history.add(new Message("user", results));                       // результат идёт обратно как сообщение
        }
        System.out.println("stop: max turns reached");
    }

    static int size(List<Message> h) { return h.toString().length(); }
}
