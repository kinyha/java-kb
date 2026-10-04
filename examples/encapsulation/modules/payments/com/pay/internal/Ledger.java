package com.pay.internal;

public class Ledger {                 // public, но пакет не экспортирован
    private long total;
    public long post(long amount) { return total += amount; }
}
