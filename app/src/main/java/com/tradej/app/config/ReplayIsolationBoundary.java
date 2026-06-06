package com.tradej.app.config;

import com.tradej.broker.api.IBrokerConnection;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

/**
 * Capital safety guard that dynamically intercepts Spring bean initialization.
 * Throws a fatal exception if any live broker connection bean (implementing IBrokerConnection)
 * or live broker adapter resides in the replay context, preventing live order leaks.
 */
@Component
public class ReplayIsolationBoundary implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof IBrokerConnection) {
            throw new IllegalStateException(
                "FATAL CAPITAL SAFETY VIOLATION: Live broker connection '" + beanName +
                "' (" + bean.getClass().getName() + ") detected in Replay Sandbox context! " +
                "Replays are strictly forbidden from loading live connections."
            );
        }

        String className = bean.getClass().getName();
        if (className.startsWith("com.tradej.broker.dhan") || className.startsWith("com.tradej.broker.upstox")) {
            throw new IllegalStateException(
                "FATAL CAPITAL SAFETY VIOLATION: Live broker adapter component '" + className +
                "' detected in Replay Sandbox context! This is strictly forbidden."
            );
        }

        return bean;
    }
}
