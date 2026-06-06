package com.tradej.broker.dhan.domain;

/**
 * Result of generating an eDIS form ({@code POST /edis/form}).
 *
 * @param clientId    Dhan client ID
 * @param edisFormHtml Escaped HTML string for the eDIS form
 */
public record DhanEdisFormResult(
        String clientId,
        String edisFormHtml
) {
}
