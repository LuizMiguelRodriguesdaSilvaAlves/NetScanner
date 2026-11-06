package com.netscanner.engine;

public final class EngineTimeoutException extends EngineCommunicationException {
    public EngineTimeoutException(String message) {
        super(message);
    }
}
