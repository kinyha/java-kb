package app;

import bank.BankClient;

public class PaymentService {
    public static void main(String[] args) {
        System.out.println("service started on Java " + Runtime.version().feature());
        BankClient client = new BankClient();     // класс BankClient загружен, но DatatypeConverter ещё нет
        System.out.println("health check: OK");
        String signature = client.sign(new byte[] {(byte) 0xCA, (byte) 0xFE});
        System.out.println("payment signed: " + signature);
    }
}
