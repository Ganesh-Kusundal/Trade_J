package com.tradej.broker.dhan.domain;

/**
 * User profile information from Dhan ({@code GET /fundlimit/userprofile}).
 *
 * @param clientId      Dhan client ID
 * @param activeSegment Active trading segments
 * @param dataPlan      Data plan status
 * @param dataValidity  Data plan validity
 * @param tokenValidity Token validity status
 * @param ddpi          DDPI limit
 * @param mtf           MTF limit
 */
public record DhanProfileInfo(
        String clientId,
        String activeSegment,
        String dataPlan,
        String dataValidity,
        String tokenValidity,
        String ddpi,
        String mtf
) {
}
