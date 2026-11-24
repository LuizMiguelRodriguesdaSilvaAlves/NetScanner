package com.netscanner.ui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** SQLite-backed storage for successful discovery and TCP port scan results. */
public final class ScanHistoryRepository {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path databasePath;
    private final String jdbcUrl;

    public ScanHistoryRepository(Path databasePath) throws IOException, SQLException {
        if (databasePath == null) {
            throw new IllegalArgumentException("databasePath must not be null.");
        }
        this.databasePath = databasePath.toAbsolutePath().normalize();
        Path parent = this.databasePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        this.jdbcUrl = "jdbc:sqlite:" + this.databasePath;
        initialize();
    }

    private Connection openConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout = 5000");
        }
        return connection;
    }

    private void initialize() throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS scan_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    scan_type TEXT NOT NULL CHECK (scan_type IN ('HOST_DISCOVERY', 'TCP_PORT_SCAN')),
                    target TEXT NOT NULL,
                    scanned_at TEXT NOT NULL,
                    scanned_count INTEGER NOT NULL DEFAULT 0,
                    active_count INTEGER NOT NULL DEFAULT 0,
                    open_count INTEGER NOT NULL DEFAULT 0,
                    service_hint_count INTEGER NOT NULL DEFAULT 0,
                    details_json TEXT NOT NULL
                )
                """;
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_scan_history_scanned_at
                    ON scan_history (id DESC)
                    """);
        }
    }

    public void saveScan(String scanType, String target, JsonNode data) throws SQLException {
        if (!"HOST_DISCOVERY".equals(scanType) && !"TCP_PORT_SCAN".equals(scanType)) {
            throw new IllegalArgumentException("Unsupported scan type: " + scanType);
        }
        if (target == null || target.isBlank() || data == null || !data.isObject()) {
            throw new IllegalArgumentException("A target and an object scan result are required.");
        }

        boolean discovery = "HOST_DISCOVERY".equals(scanType);
        long scannedCount = data.path(discovery ? "scanned_hosts" : "scanned_ports").asLong(0);
        long openCount = data.path("open_count").asLong(0);
        long activeCount = discovery ? data.path("active_hosts").asLong(0) : openCount;
        long serviceHintCount = 0;
        JsonNode results = data.path("results");
        if (results.isArray()) {
            for (JsonNode result : results) {
                if (result.path("service_hint").isTextual()) {
                    serviceHintCount++;
                }
            }
        }
        String scannedAt = data.path("scanned_at").asText(Instant.now().toString());
        String details;
        try {
            details = MAPPER.writeValueAsString(data);
        } catch (JsonProcessingException exception) {
            throw new SQLException("Could not serialize scan details.", exception);
        }

        String sql = """
                INSERT INTO scan_history
                    (scan_type, target, scanned_at, scanned_count, active_count,
                     open_count, service_hint_count, details_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, scanType);
            statement.setString(2, target);
            statement.setString(3, scannedAt);
            statement.setLong(4, scannedCount);
            statement.setLong(5, activeCount);
            statement.setLong(6, openCount);
            statement.setLong(7, serviceHintCount);
            statement.setString(8, details);
            statement.executeUpdate();
        }
    }

    public List<ScanHistoryEntry> findRecent(int limit) throws SQLException {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100.");
        }
        String sql = """
                SELECT id, scan_type, target, scanned_at, scanned_count,
                       active_count, open_count, service_hint_count
                FROM scan_history
                ORDER BY id DESC
                LIMIT ?
                """;
        List<ScanHistoryEntry> entries = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    entries.add(new ScanHistoryEntry(
                            result.getLong("id"),
                            result.getString("scan_type"),
                            result.getString("target"),
                            result.getString("scanned_at"),
                            result.getLong("scanned_count"),
                            result.getLong("active_count"),
                            result.getLong("open_count"),
                            result.getLong("service_hint_count")
                    ));
                }
            }
        }
        return entries;
    }

    public DashboardMetrics loadDashboardMetrics() throws SQLException {
        try (Connection connection = openConnection()) {
            long totalScans = scalarLong(connection, "SELECT COUNT(*) FROM scan_history");
            long activeHosts = latestValue(connection, "HOST_DISCOVERY", "active_count");
            long openPorts = latestValue(connection, "TCP_PORT_SCAN", "open_count");
            long serviceHints = latestValue(connection, "TCP_PORT_SCAN", "service_hint_count");
            return new DashboardMetrics(totalScans, activeHosts, openPorts, serviceHints);
        }
    }

    private static long scalarLong(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            return result.next() ? result.getLong(1) : 0;
        }
    }

    private static long latestValue(Connection connection, String scanType, String column) throws SQLException {
        // Column names are internal constants passed by this class, never user input.
        String sql = "SELECT " + column + " FROM scan_history WHERE scan_type = ? ORDER BY id DESC LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, scanType);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong(1) : 0;
            }
        }
    }

    public Path databasePath() {
        return databasePath;
    }
}
