import audit.Audited;

class Refunds {
    @Audited("refund") public void refund(String id) { }
    @Audited("cleanup") void cleanup() { }            // не public — обработчик остановит сборку
}
