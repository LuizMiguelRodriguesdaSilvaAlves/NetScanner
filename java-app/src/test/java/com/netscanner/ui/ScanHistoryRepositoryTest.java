package com.netscanner.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScanHistoryRepositoryTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path temporaryDirectory;

    @Test
    void storesDiscoveryAndLoadsDashboardMetrics() throws Exception {
        ScanHistoryRepository repository =
                new ScanHistoryRepository(temporaryDirectory.resolve("history.db"));
        ObjectNode data = MAPPER.createObjectNode();
        data.put("target", "127.0.0.1/30");
        data.put("scanned_hosts", 2);
        data.put("active_hosts", 1);
        data.put("scanned_at", "2026-11-24T12:00:00Z");
        data.set("hosts", MAPPER.createArrayNode());

        repository.saveScan("HOST_DISCOVERY", "127.0.0.1/30", data);

        DashboardMetrics metrics = repository.loadDashboardMetrics();
        assertEquals(1, metrics.totalScans());
        assertEquals(1, metrics.activeHosts());
        assertEquals(0, metrics.openPorts());
        assertEquals(0, metrics.serviceHints());
    }

    @Test
    void storesPortScanAndCountsOnlyKnownServiceHints() throws Exception {
        ScanHistoryRepository repository =
                new ScanHistoryRepository(temporaryDirectory.resolve("history.db"));
        ObjectNode data = MAPPER.createObjectNode();
        data.put("target", "127.0.0.1");
        data.put("scanned_ports", 3);
        data.put("open_count", 2);
        data.put("scanned_at", "2026-11-24T12:05:00Z");
        ArrayNode results = data.putArray("results");
        results.addObject().put("port", 22).put("state", "open").put("service_hint", "SSH");
        results.addObject().put("port", 8080).put("state", "open").put("service_hint", "HTTP alternate");
        results.addObject().put("port", 65000).put("state", "closed").putNull("service_hint");

        repository.saveScan("TCP_PORT_SCAN", "127.0.0.1", data);

        DashboardMetrics metrics = repository.loadDashboardMetrics();
        assertEquals(1, metrics.totalScans());
        assertEquals(2, metrics.openPorts());
        assertEquals(2, metrics.serviceHints());

        List<ScanHistoryEntry> recent = repository.findRecent(5);
        assertEquals(1, recent.size());
        assertEquals("TCP_PORT_SCAN", recent.get(0).scanType());
        assertTrue(recent.get(0).summaryLabel().contains("2 open"));
    }

    @Test
    void returnsRecentScansNewestFirst() throws SQLException {
        ScanHistoryRepository repository =
                new ScanHistoryRepository(temporaryDirectory.resolve("history.db"));
        ObjectNode first = MAPPER.createObjectNode().put("scanned_hosts", 4).put("active_hosts", 1);
        ObjectNode second = MAPPER.createObjectNode().put("scanned_hosts", 8).put("active_hosts", 3);

        repository.saveScan("HOST_DISCOVERY", "127.0.0.1/30", first);
        repository.saveScan("HOST_DISCOVERY", "127.0.0.1/29", second);

        List<ScanHistoryEntry> recent = repository.findRecent(2);
        assertEquals(2, recent.size());
        assertEquals("127.0.0.1/29", recent.get(0).target());
        assertEquals("127.0.0.1/30", recent.get(1).target());
    }
}
