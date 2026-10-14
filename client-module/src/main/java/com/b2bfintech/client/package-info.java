/**
 * Client-side domain code that doesn't belong alongside billing/wallet - currently just support
 * tickets ({@link com.b2bfintech.client.entity.SupportTicket}). Managed bills (auto-fetch/auto-pay
 * config plus the latest fetched state) live on {@link com.b2bfintech.billing.entity.Bill} in
 * {@code billing-core} instead, since that's also where {@code BillPayment}/{@code BillPaymentLog}
 * live and a client's bill account needs to sit alongside them.
 */
package com.b2bfintech.client;
