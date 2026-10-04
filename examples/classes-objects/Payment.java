// Разбирается javap: куда попадают инициализаторы полей и блоки при нескольких конструкторах.
public class Payment {
    private final String id;
    private final long amount;
    private String currency = "RUB";          // инициализатор поля
    { System.out.println("init block"); }       // блок инициализации экземпляра

    public Payment(String id, long amount, String currency) {
        this.id = id;
        this.amount = amount;
        this.currency = currency;
    }

    public Payment(String id, long amount) {
        this(id, amount, "RUB");                // делегирование: инициализаторы здесь не повторяются
    }
}
