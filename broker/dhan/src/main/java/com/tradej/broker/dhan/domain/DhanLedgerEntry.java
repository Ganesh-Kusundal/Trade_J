package com.tradej.broker.dhan.domain;

/**
 * A single entry from the Dhan ledger report ({@code GET /ledger}).
 *
 * @param clientId      Dhan client ID
 * @param narration     Transaction narration/description
 * @param voucherDate   Date of the voucher (YYYY-MM-DD)
 * @param exchange      Exchange name
 * @param voucherDesc   Voucher description
 * @param voucherNumber Voucher reference number
 * @param debit         Debit amount in paisa
 * @param credit        Credit amount in paisa
 * @param runningBalance Running balance in paisa
 */
public record DhanLedgerEntry(
        String clientId,
        String narration,
        String voucherDate,
        String exchange,
        String voucherDesc,
        String voucherNumber,
        long debit,
        long credit,
        long runningBalance
) {
}
