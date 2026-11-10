package com.netscanner.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netscanner.config.EngineConfiguration;
import com.netscanner.engine.PythonEngineClient;
import com.netscanner.model.EngineResponse;

import java.util.HashMap;
import java.util.Map;

public final class NetScannerApplication {
    private static final int DEFAULT_TIMEOUT_MS = 500;
    private static final int DEFAULT_MAX_WORKERS = 64;

    private NetScannerApplication() {
    }

    public static void main(String[] args) throws Exception {
        EngineResponse response;
        String operation;

        if (args.length == 0) {
            operation = "ping";
            response = createClient().ping();
        } else if ("--discover".equals(args[0])) {
            if (args.length != 2) {
                printUsage();
                System.exit(2);
                return;
            }
            operation = "discovery";
            response = createClient().execute("network.host_discovery", Map.of("cidr", args[1]));
        } else if ("--scan".equals(args[0])) {
            ScanOptions options;
            try {
                options = parseScanOptions(args);
            } catch (IllegalArgumentException exception) {
                System.err.println("Invalid scan arguments: " + exception.getMessage());
                printUsage();
                System.exit(2);
                return;
            }
            operation = "port scan";
            Map<String, Object> payload = new HashMap<>();
            payload.put("target", options.target());
            payload.put("ports", options.ports());
            payload.put("timeout_ms", options.timeoutMs());
            payload.put("max_workers", options.maxWorkers());
            response = createClient().execute("network.port_scan", payload);
        } else {
            printUsage();
            System.exit(2);
            return;
        }

        ObjectMapper mapper = new ObjectMapper();
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(response));

        if (!response.success()) {
            System.err.println("Python engine reported an error: " + response.error());
            System.exit(1);
            return;
        }

        if ("discovery".equals(operation)) {
            int scannedHosts = response.data().path("scanned_hosts").asInt();
            int activeHosts = response.data().path("active_hosts").asInt();
            System.out.printf("Host discovery completed: %d active host(s) among %d scanned.%n",
                    activeHosts, scannedHosts);
        } else if ("port scan".equals(operation)) {
            int scannedPorts = response.data().path("scanned_ports").asInt();
            int openPorts = response.data().path("open_count").asInt();
            int closedPorts = response.data().path("closed_count").asInt();
            int filteredPorts = response.data().path("filtered_count").asInt();
            System.out.printf(
                    "TCP port scan completed: %d open, %d closed and %d filtered/inconclusive port(s) among %d scanned.%n",
                    openPorts, closedPorts, filteredPorts, scannedPorts
            );
        } else {
            System.out.println("NetScanner Java/Python integration: OK");
        }
    }

    private static PythonEngineClient createClient() {
        EngineConfiguration configuration = EngineConfiguration.fromEnvironment();
        return new PythonEngineClient(
                configuration.pythonExecutable(),
                configuration.engineDirectory(),
                configuration.timeout()
        );
    }

    private static ScanOptions parseScanOptions(String[] args) {
        if (args.length < 4 || !"--ports".equals(args[2])) {
            throw new IllegalArgumentException("--scan requires a target and --ports <list-or-range>.");
        }

        String target = args[1];
        String ports = args[3];
        int timeoutMs = DEFAULT_TIMEOUT_MS;
        int maxWorkers = DEFAULT_MAX_WORKERS;
        boolean timeoutProvided = false;
        boolean workersProvided = false;

        for (int index = 4; index < args.length; index += 2) {
            if (index + 1 >= args.length) {
                throw new IllegalArgumentException("Every option must have a value.");
            }
            String option = args[index];
            String value = args[index + 1];
            switch (option) {
                case "--timeout-ms" -> {
                    if (timeoutProvided) {
                        throw new IllegalArgumentException("--timeout-ms was provided more than once.");
                    }
                    timeoutMs = parseInteger(option, value);
                    timeoutProvided = true;
                }
                case "--max-workers" -> {
                    if (workersProvided) {
                        throw new IllegalArgumentException("--max-workers was provided more than once.");
                    }
                    maxWorkers = parseInteger(option, value);
                    workersProvided = true;
                }
                default -> throw new IllegalArgumentException("Unknown option: " + option);
            }
        }

        return new ScanOptions(target, ports, timeoutMs, maxWorkers);
    }

    private static int parseInteger(String option, String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(option + " must be an integer.", exception);
        }
    }

    private static void printUsage() {
        System.err.println("Usage:");
        System.err.println("  mvn exec:java");
        System.err.println("  mvn exec:java -Dexec.args=\"--discover <IPv4-CIDR>\"");
        System.err.println("  mvn exec:java -Dexec.args=\"--scan <private-IPv4> --ports <22,80,443,8000-8100> [--timeout-ms 500] [--max-workers 64]\"");
    }

    private record ScanOptions(String target, String ports, int timeoutMs, int maxWorkers) {
    }
}
