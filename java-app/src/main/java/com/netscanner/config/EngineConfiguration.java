package com.netscanner.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public record EngineConfiguration(String pythonExecutable, Path engineDirectory, Duration timeout) {
    public static EngineConfiguration fromEnvironment() {
        String python = valueOrDefault(System.getenv("NETSCANNER_PYTHON"), "python");
        String configuredDirectory = System.getenv("NETSCANNER_PYTHON_ENGINE");
        Path engineDirectory = configuredDirectory == null || configuredDirectory.isBlank()
                ? findEngineDirectory()
                : Path.of(configuredDirectory).toAbsolutePath().normalize();
        long timeoutSeconds = parseTimeout(System.getenv("NETSCANNER_TIMEOUT_SECONDS"));
        return new EngineConfiguration(python, engineDirectory, Duration.ofSeconds(timeoutSeconds));
    }

    private static Path findEngineDirectory() {
        Path current = Path.of("").toAbsolutePath().normalize();
        List<Path> candidates = new ArrayList<>();
        Path cursor = current;
        for (int i = 0; i < 5 && cursor != null; i++, cursor = cursor.getParent()) {
            candidates.add(cursor.resolve("python-engine"));
        }
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate.resolve("src/netscanner"))) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Could not locate python-engine. Set NETSCANNER_PYTHON_ENGINE to its directory."
        );
    }

    private static long parseTimeout(String value) {
        if (value == null || value.isBlank()) {
            return 10;
        }
        try {
            long seconds = Long.parseLong(value);
            if (seconds < 1 || seconds > 300) {
                throw new IllegalArgumentException("NETSCANNER_TIMEOUT_SECONDS must be between 1 and 300.");
            }
            return seconds;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("NETSCANNER_TIMEOUT_SECONDS must be an integer.", exception);
        }
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
