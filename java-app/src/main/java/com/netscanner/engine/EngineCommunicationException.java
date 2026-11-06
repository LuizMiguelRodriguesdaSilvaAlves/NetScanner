package com.netscanner.engine;

public class EngineCommunicationException extends RuntimeException {
    public EngineCommunicationException(String message) {
        super(message);
    }

    public EngineCommunicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
