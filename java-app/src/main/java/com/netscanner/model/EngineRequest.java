package com.netscanner.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Objects;

public record EngineRequest(
        @JsonProperty("protocol_version") String protocolVersion,
        @JsonProperty("request_id") String requestId,
        String action,
        Map<String, Object> payload
) {
    public EngineRequest {
        if (protocolVersion == null || protocolVersion.isBlank()) {
            throw new IllegalArgumentException("protocolVersion must not be blank");
        }
        if (requestId == null || requestId.isBlank() || requestId.length() > 128) {
            throw new IllegalArgumentException("requestId must contain 1 to 128 characters");
        }
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action must not be blank");
        }
        payload = Map.copyOf(Objects.requireNonNull(payload, "payload must not be null"));
    }
}
