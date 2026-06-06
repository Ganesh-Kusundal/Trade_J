package com.tradej.broker.dhan.domain;

/**
 * Result of an eDIS inquiry ({@code GET /edis/inquire/{isin}}).
 *
 * @param clientId    Dhan client ID
 * @param isin        ISIN of the security
 * @param totalQty    Total quantity held
 * @param approvedQty Quantity approved for pledge
 * @param status      Status of the eDIS request
 * @param remarks     Additional remarks
 */
public record DhanEdisInquiryResult(
        String clientId,
        String isin,
        long totalQty,
        long approvedQty,
        String status,
        String remarks
) {
}
