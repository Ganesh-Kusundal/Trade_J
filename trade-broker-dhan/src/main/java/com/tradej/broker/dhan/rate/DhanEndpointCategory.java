package com.tradej.broker.dhan.rate;

import static com.tradej.broker.dhan.constants.DhanApiEndpoints.PATH_PREFIX_ALERTS;
import static com.tradej.broker.dhan.constants.DhanApiEndpoints.PATH_PREFIX_FOREVER_ORDERS;
import static com.tradej.broker.dhan.constants.DhanApiEndpoints.PATH_PREFIX_FUNDS;
import static com.tradej.broker.dhan.constants.DhanApiEndpoints.PATH_PREFIX_HOLDINGS;
import static com.tradej.broker.dhan.constants.DhanApiEndpoints.PATH_PREFIX_MARGIN;
import static com.tradej.broker.dhan.constants.DhanApiEndpoints.PATH_PREFIX_OPTION_CHAIN;
import static com.tradej.broker.dhan.constants.DhanApiEndpoints.PATH_PREFIX_ORDERS;
import static com.tradej.broker.dhan.constants.DhanApiEndpoints.PATH_PREFIX_POSITIONS;
import static com.tradej.broker.dhan.constants.DhanApiEndpoints.PATH_PREFIX_PNL_EXIT;
import static com.tradej.broker.dhan.constants.DhanApiEndpoints.PATH_PREFIX_PROFILE;
import static com.tradej.broker.dhan.constants.DhanApiEndpoints.PATH_PREFIX_QUOTE;
import static com.tradej.broker.dhan.constants.DhanApiEndpoints.PATH_PREFIX_SUPER_ORDER;

public final class DhanEndpointCategory {
    private DhanEndpointCategory() {
    }

    public static ApiCategory forPath(String path) {
        if (path == null || path.isBlank()) {
            return ApiCategory.DATA;
        }
        if (path.startsWith(PATH_PREFIX_ORDERS) || path.startsWith(PATH_PREFIX_ALERTS) || path.startsWith(PATH_PREFIX_MARGIN)
                || path.startsWith(PATH_PREFIX_FOREVER_ORDERS) || path.startsWith(PATH_PREFIX_SUPER_ORDER)) {
            return ApiCategory.ORDER;
        }
        if (path.startsWith(PATH_PREFIX_QUOTE)) {
            return ApiCategory.QUOTE;
        }
        if (path.startsWith(PATH_PREFIX_OPTION_CHAIN)) {
            return ApiCategory.OPTION_CHAIN;
        }
        if (path.startsWith(PATH_PREFIX_HOLDINGS) || path.startsWith(PATH_PREFIX_POSITIONS) || path.startsWith(PATH_PREFIX_FUNDS)
                || path.startsWith(PATH_PREFIX_PROFILE) || path.startsWith(PATH_PREFIX_PNL_EXIT)) {
            return ApiCategory.NON_TRADING;
        }
        return ApiCategory.DATA;
    }
}
