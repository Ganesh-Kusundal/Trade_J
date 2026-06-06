package com.tradej.broker.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method, class, or field as an internal broker-specific contract that is
 * exposed in the public SPI for adapter implementation reasons but is not part of the
 * stable, generic broker-gateway surface.
 *
 * <p>Callers outside the {@code broker.*} modules should treat the annotated element
 * as implementation detail. The signature, semantics, and even the existence of
 * {@code @BrokerInternal} APIs can change between releases without following the
 * standard SPI stability rules.
 *
 * <p>The intent is to keep the broker-gateway dynamic-dispatch surface canonical
 * (operating on {@link com.tradej.core.domain.model.InstrumentKey} +
 * {@link com.tradej.core.domain.value.ExchangeSegment} + canonical domain records)
 * while allowing individual broker modules to plug in SDK-native lookup methods
 * (e.g. Dhan's {@code securityId}, Upstox's instrument key strings, ICICI's
 * {@code stock_code}).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER})
public @interface BrokerInternal {
    /**
     * Why this element is exposed. Helps reviewers understand whether the leak is
     * intentional (e.g. for SDK bridging) or an oversight.
     */
    String reason() default "";
}
