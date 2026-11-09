package com.netscanner.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netscanner.config.EngineConfiguration;
import com.netscanner.engine.PythonEngineClient;
import com.netscanner.model.EngineResponse;

import java.util.Map;

public final class NetScannerApplication {
    private NetScannerApplication() {
    }

    public static void main(String[] args) throws Exception {
        boolean discoveryRequested = args.length > 0;
        if (discoveryRequested && (args.length != 2 || !"--discover".equals(args[0]))) {
            System.err.println("Usage: mvn exec:java -Dexec.args=\"--discover <IPv4-CIDR>\"");
            System.exit(2);
            return;
        }

        EngineConfiguration configuration = EngineConfiguration.fromEnvironment();
        PythonEngineClient engineClient = new PythonEngineClient(
                configuration.pythonExecutable(),
                configuration.engineDirectory(),
                configuration.timeout()
        );

        EngineResponse response = discoveryRequested
                ? engineClient.execute("network.host_discovery", Map.of("cidr", args[1]))
                : engineClient.ping();

        ObjectMapper mapper = new ObjectMapper();
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(response));

        if (!response.success()) {
            System.err.println("Python engine reported an error: " + response.error());
            System.exit(1);
        }

        if (discoveryRequested) {
            int scannedHosts = response.data().path("scanned_hosts").asInt();
            int activeHosts = response.data().path("active_hosts").asInt();
            System.out.printf("Host discovery completed: %d active host(s) among %d scanned.%n",
                    activeHosts, scannedHosts);
        } else {
            System.out.println("NetScanner Java/Python integration: OK");
        }
    }
}
