package bank;

import javax.xml.bind.DatatypeConverter; // Java EE API: в JDK 8 есть, в JDK 11 модуль java.xml.bind удалён (JEP 320)

// Клиент банковского шлюза из «старой» библиотеки: собран под Java 8.
public class BankClient {
    public String sign(byte[] payload) {
        // Подпись запроса в hex — так делали многие библиотеки времён Java 8.
        return DatatypeConverter.printHexBinary(payload);
    }
}
