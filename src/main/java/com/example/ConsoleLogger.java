package com.example;

import io.sentry.ILogger;
import io.sentry.SentryLevel;

/**
 * Simple console logger for debugging Sentry transport routing
 */
public class ConsoleLogger implements ILogger {

    @Override
    public void log(SentryLevel level, String message, Object... args) {
        String formattedMessage = String.format(message, args);
        System.out.printf("[SENTRY %s] %s%n", level, formattedMessage);
    }

    @Override
    public void log(SentryLevel level, String message, Throwable throwable) {
        System.out.printf("[SENTRY %s] %s%n", level, message);
        if (throwable != null) {
            throwable.printStackTrace();
        }
    }

    @Override
    public void log(SentryLevel level, Throwable throwable, String message, Object... args) {
        String formattedMessage = String.format(message, args);
        System.out.printf("[SENTRY %s] %s%n", level, formattedMessage);
        if (throwable != null) {
            throwable.printStackTrace();
        }
    }

    @Override
    public boolean isEnabled(SentryLevel level) {
        return true;
    }
}

