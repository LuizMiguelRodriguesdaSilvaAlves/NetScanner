package com.netscanner.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netscanner.config.EngineConfiguration;
import com.netscanner.engine.PythonEngineClient;
import com.netscanner.model.EngineResponse;

public final class NetScannerApplication {
    private NetScannerApplication() {
    }

    public static void main(String[] args) throws Exception {
        EngineConfiguration configuration = EngineConfiguration.fromEnvironment();
        PythonEngineClient engineClient = new PythonEngineClient(
                configuration.pythonExecutable(),
                configuration.engineDirectory(),
                configuration.timeout()
        );

        EngineResponse response = engineClient.ping();
        ObjectMapper mapper = new ObjectMapper();
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(response));

        if (!response.success()) {
            System.err.println("Python engine reported an error: " + response.error());
            System.exit(1);
        }
        System.out.println("NetScanner Java/Python integration: OK");
    }
}
