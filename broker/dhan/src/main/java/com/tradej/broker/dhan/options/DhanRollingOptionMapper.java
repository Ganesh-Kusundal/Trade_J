package com.tradej.broker.dhan.options;

import com.tradej.broker.dhan.mapper.DhanJsonResponse;
import com.tradej.core.domain.model.RollingOptionBar;
import com.tradej.core.domain.model.RollingOptionHistoryRequest;
import com.tradej.core.domain.model.RollingOptionSeries;
import com.tradej.core.domain.value.OptionType;
import com.tradej.core.domain.value.PriceMath;

import java.util.ArrayList;
import java.util.List;

final class DhanRollingOptionMapper {

    List<RollingOptionBar> toBars(DhanJsonResponse payload, RollingOptionHistoryRequest request) {
        DhanJsonResponse root = payload.has("data") ? payload.path("data") : payload;
        OptionType optionType = request.series().optionType();
        DhanJsonResponse side = root.path(DhanRollingOptionWireMapper.responseSideKey(optionType));
        if (side.isMissingNode() || side.isNull() || !side.isObject()) {
            return List.of();
        }

        DhanJsonResponse open = requiredArray(side, "open");
        DhanJsonResponse high = requiredArray(side, "high");
        DhanJsonResponse low = requiredArray(side, "low");
        DhanJsonResponse close = requiredArray(side, "close");
        DhanJsonResponse volume = requiredArray(side, "volume");
        DhanJsonResponse iv = requiredArray(side, "iv");
        DhanJsonResponse oi = requiredArray(side, "oi");
        DhanJsonResponse spot = requiredArray(side, "spot");
        DhanJsonResponse strike = requiredArray(side, "strike");
        DhanJsonResponse timestamp = side.has("timestamp")
                ? requiredArray(side, "timestamp")
                : requiredArray(side, "start_Time");

        int size = timestamp.size();
        if (size == 0) {
            return List.of();
        }
        validateAligned(size, open, high, low, close, volume, iv, oi, spot, strike, request);

        List<RollingOptionBar> bars = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            bars.add(new RollingOptionBar(
                    toEpochMillis(timestamp.get(i)),
                    PriceMath.toPaisa(open.get(i).asText()),
                    PriceMath.toPaisa(high.get(i).asText()),
                    PriceMath.toPaisa(low.get(i).asText()),
                    PriceMath.toPaisa(close.get(i).asText()),
                    volume.get(i).asLong(),
                    iv.get(i).asDouble(),
                    oi.get(i).asLong(),
                    PriceMath.toPaisa(spot.get(i).asText()),
                    PriceMath.toPaisa(strike.get(i).asText())
            ));
        }
        return List.copyOf(bars);
    }

    RollingOptionSeries toSeries(DhanJsonResponse payload, RollingOptionHistoryRequest request) {
        return new RollingOptionSeries(request, toBars(payload, request));
    }

    private static void validateAligned(
            int size,
            DhanJsonResponse open,
            DhanJsonResponse high,
            DhanJsonResponse low,
            DhanJsonResponse close,
            DhanJsonResponse volume,
            DhanJsonResponse iv,
            DhanJsonResponse oi,
            DhanJsonResponse spot,
            DhanJsonResponse strike,
            RollingOptionHistoryRequest request
    ) {
        if (open.size() != size || high.size() != size || low.size() != size || close.size() != size
                || volume.size() != size || iv.size() != size || oi.size() != size
                || spot.size() != size || strike.size() != size) {
            throw new IllegalStateException("Dhan rolling option payload arrays are misaligned for "
                    + request.series().underlying() + " offset="
                    + request.series().strikeOffset().value() + " " + request.series().optionType());
        }
    }

    private static DhanJsonResponse requiredArray(DhanJsonResponse payload, String field) {
        DhanJsonResponse value = payload.path(field);
        if (!value.isArray()) {
            throw new IllegalStateException("Dhan rolling option payload missing array field `" + field + "`");
        }
        return value;
    }

    private static long toEpochMillis(DhanJsonResponse value) {
        long raw = value.asLong();
        return raw > 100_000_000_000L ? raw : raw * 1000L;
    }
}
