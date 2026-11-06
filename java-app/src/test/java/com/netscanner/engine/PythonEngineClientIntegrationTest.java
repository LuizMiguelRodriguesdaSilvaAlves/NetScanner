package com.netscanner.engine;

import com.netscanner.model.EngineResponse;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonEngineClientIntegrationTest {
    @Test
    void javaCanStartPythonEngineAndReceivePingResponse() {
        String python = System.getenv().getOrDefault("NETSCANNER_PYTHON", "python");
        Path engineDirectory = Path.of("..", "python-engine").toAbsolutePath().normalize();
        PythonEngineClient client = new PythonEngineClient(python, engineDirectory, Duration.ofSeconds(10));

        EngineResponse response = client.execute("system.ping", Map.of());

        assertTrue(response.success());
        assertNotNull(response.data());
        assertEquals("ready", response.data().get("status").asText());
        assertEquals("NetScanner Python Engine", response.data().get("engine").asText());
    }
}
