package com.app;

import com.pay.api.Payments;

public class Main {
    public static void main(String[] args) throws Exception {
        Payments p = new Payments();
        System.out.println("pay via exported API: " + p.pay(500));
        var f = Payments.class.getDeclaredField("ledger");
        try {
            f.setAccessible(true);
            System.out.println("deep reflection into payments: allowed");
        } catch (java.lang.reflect.InaccessibleObjectException e) {
            System.out.println("deep reflection into payments: InaccessibleObjectException");
        }
    }
}
