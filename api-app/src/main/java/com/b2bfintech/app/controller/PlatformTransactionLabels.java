package com.b2bfintech.app.controller;

import com.b2bfintech.wallet.entity.PlatformTransaction;

/** The phrase shown as What on the Paytrix transactions list when a row has no note of its own. */
final class PlatformTransactionLabels {

    private PlatformTransactionLabels() {
    }

    static String what(String category, PlatformTransaction.Type type) {
        if ("RECHARGE".equals(category)) {
            return "Wallet recharge";
        }
        if ("ADJUSTMENT".equals(category)) {
            return "Manual wallet credit";
        }
        if ("FEE".equals(category)) {
            return type == PlatformTransaction.Type.CREDIT ? "Convenience fee" : "Fee refund";
        }
        if ("BILL_PAID".equals(category)) {
            return "Paid the vendor";
        }
        if ("BILLER_PAID".equals(category)) {
            return "Paid the biller";
        }
        if ("COMMISSION".equals(category)) {
            return "Commission received";
        }
        String label = category == null || category.isBlank()
                ? "Bill"
                : Character.toUpperCase(category.charAt(0)) + category.substring(1).toLowerCase().replace('_', ' ');
        return type == PlatformTransaction.Type.CREDIT ? label + " bill payment" : label + " refund";
    }
}
