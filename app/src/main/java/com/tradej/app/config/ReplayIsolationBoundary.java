package com.tradej.app.config;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.core.domain.runtime.RuntimeMode;
import com.tradej.core.domain.runtime.RuntimeModeHolder;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

/**
 * Capital safety guard that dynamically intercepts Spring bean initialization.
 * Throws a fatal exception if any live broker connection bean (implementing IBrokerConnection)
 * or live broker adapter resides in the replay context, preventing live order leaks.
 * Only active when runtime mode is REPLAY or BACKTEST.
 */
@Component
public class ReplayIsolationBoundary implements BeanPostProcessor {

    private final RuntimeModeHolder modeHolder;

    public ReplayIsolationBoundary(RuntimeModeHolder modeHolder) {
        this.modeHolder = modeHolder;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        RuntimeMode mode = modeHolder.mode();
        if (mode != RuntimeMode.REPLAY && mode != RuntimeMode.BACKTEST) {
            return bean;
        }

        if (bean instanceof IBrokerConnection) {
            throw new IllegalStateException(
                "FATAL CAPITAL SAFETY VIOLATION: Live broker connection '" + beanName +
                "' (" + bean.getClass().getName() + ") detected in " + mode + " context! " +
                "Replays are strictly forbidden from loading live connections."
            );
        }

        String className = bean.getClass().getName();
        if (className.startsWith("com.tradej.broker.dhan") || className.startsWith("com.tradej.broker.upstox")) {
            throw new IllegalStateException(
                "FATAL CAPITAL SAFETY VIOLATION: Live broker adapter component '" + className +
                "' detected in " + mode + " context! This is strictly forbidden."
            );
        }

        return bean;
    }
}
