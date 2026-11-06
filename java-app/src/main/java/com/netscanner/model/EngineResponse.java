package com.netscanner.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

public record EngineResponse(
        @JsonProperty("protocol_version") String protocolVersion,
        @JsonProperty("request_id") String requestId,
        boolean success,
        JsonNode data,
        EngineError error
) {
}
