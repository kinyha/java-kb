package kb.component.payments;

public interface PaymentComponent {               // Единственный вход в компонент.
    Payment pay(String id, long amount);
    Payment find(String id);
}
