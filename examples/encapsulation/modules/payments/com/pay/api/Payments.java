package com.pay.api;

import com.pay.internal.Ledger;

public class Payments {
    private final Ledger ledger = new Ledger();
    public long pay(long amount) { return ledger.post(amount); }
}
