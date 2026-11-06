package com.netscanner.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netscanner.model.EngineRequest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EngineRequestTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void serializesProtocolFieldsUsingSnakeCase() throws Exception {
        EngineRequest request = new EngineRequest("1.0", "request-123", "system.ping", Map.of());
        String json = mapper.writeValueAsString(request);

        assertEquals("1.0", mapper.readTree(json).get("protocol_version").asText());
        assertEquals("request-123", mapper.readTree(json).get("request_id").asText());
        assertEquals("system.ping", mapper.readTree(json).get("action").asText());
    }

    @Test
    void rejectsBlankRequestId() {
        assertThrows(IllegalArgumentException.class,
                () -> new EngineRequest("1.0", " ", "system.ping", Map.of()));
    }

    @Test
    void rejectsBlankAction() {
        assertThrows(IllegalArgumentException.class,
                () -> new EngineRequest("1.0", "request-123", "", Map.of()));
    }
}
