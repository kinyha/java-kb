package com.app;

import com.pay.internal.Ledger;       // пакет не экспортирован

public class BadMain {
    public static void main(String[] args) { new Ledger().post(1); }
}
