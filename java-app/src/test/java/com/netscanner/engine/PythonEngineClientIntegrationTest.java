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
        PythonEngineClient client = createClient();

        EngineResponse response = client.execute("system.ping", Map.of());

        assertTrue(response.success());
        assertNotNull(response.data());
        assertEquals("ready", response.data().get("status").asText());
        assertEquals("NetScanner Python Engine", response.data().get("engine").asText());
    }

    @Test
    void javaCanRequestHostDiscoveryAndReceiveStructuredResults() {
        PythonEngineClient client = createClient();

        EngineResponse response = client.execute(
                "network.host_discovery",
                Map.of("cidr", "127.0.0.1/32")
        );

        assertTrue(response.success(), () -> "Expected successful discovery but received: " + response.error());
        assertNotNull(response.data());
        assertEquals("127.0.0.1/32", response.data().get("target").asText());
        assertEquals(1, response.data().get("scanned_hosts").asInt());
        assertTrue(response.data().get("active_hosts").asInt() >= 0);
        assertTrue(response.data().get("hosts").isArray());
    }

    private static PythonEngineClient createClient() {
        String python = System.getenv().getOrDefault("NETSCANNER_PYTHON", "python");
        Path engineDirectory = Path.of("..", "python-engine").toAbsolutePath().normalize();
        return new PythonEngineClient(python, engineDirectory, Duration.ofSeconds(30));
    }
}
