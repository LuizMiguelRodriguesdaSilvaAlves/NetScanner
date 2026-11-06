package com.netscanner.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netscanner.model.EngineRequest;
import com.netscanner.model.EngineResponse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Starts one Python engine process per request and exchanges JSON over stdin/stdout. */
public final class PythonEngineClient {
    public static final String PROTOCOL_VERSION = "1.0";

    private final String pythonExecutable;
    private final Path engineDirectory;
    private final Duration timeout;
    private final ObjectMapper objectMapper;

    public PythonEngineClient(String pythonExecutable, Path engineDirectory, Duration timeout) {
        if (pythonExecutable == null || pythonExecutable.isBlank()) {
            throw new IllegalArgumentException("pythonExecutable must not be blank");
        }
        if (engineDirectory == null || !Files.isDirectory(engineDirectory.resolve("src/netscanner"))) {
            throw new IllegalArgumentException("engineDirectory must contain src/netscanner");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        this.pythonExecutable = pythonExecutable;
        this.engineDirectory = engineDirectory.toAbsolutePath().normalize();
        this.timeout = timeout;
        this.objectMapper = new ObjectMapper();
    }

    public EngineResponse ping() {
        return execute("system.ping", Map.of());
    }

    public EngineResponse execute(String action, Map<String, Object> payload) {
        EngineRequest request = new EngineRequest(
                PROTOCOL_VERSION,
                UUID.randomUUID().toString(),
                action,
                payload
        );
        return execute(request);
    }

    public EngineResponse execute(EngineRequest request) {
        final String requestJson;
        try {
            requestJson = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new EngineCommunicationException("Could not serialize the engine request.", exception);
        }

        Process process;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(pythonExecutable, "-m", "netscanner");
            processBuilder.directory(engineDirectory.toFile());
            processBuilder.environment().put("PYTHONUNBUFFERED", "1");
            String sourceDirectory = engineDirectory.resolve("src").toString();
            String currentPythonPath = processBuilder.environment().get("PYTHONPATH");
            String pythonPath = currentPythonPath == null || currentPythonPath.isBlank()
                    ? sourceDirectory
                    : sourceDirectory + System.getProperty("path.separator") + currentPythonPath;
            processBuilder.environment().put("PYTHONPATH", pythonPath);
            process = processBuilder.start();
        } catch (IOException exception) {
            throw new EngineCommunicationException(
                    "Could not start Python. Check NETSCANNER_PYTHON and the engine installation.", exception
            );
        }

        CompletableFuture<byte[]> stdout = readAsync(process.getInputStream());
        CompletableFuture<byte[]> stderr = readAsync(process.getErrorStream());

        try (var stdin = process.getOutputStream()) {
            stdin.write(requestJson.getBytes(StandardCharsets.UTF_8));
            stdin.write('\n');
            stdin.flush();
        } catch (IOException exception) {
            process.destroyForcibly();
            throw new EngineCommunicationException("Could not send the request to the Python engine.", exception);
        }

        try {
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroy();
                if (!process.waitFor(250, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                }
                throw new EngineTimeoutException("Python engine did not respond within " + timeout.toSeconds() + " seconds.");
            }

            String stdoutText = new String(stdout.get(2, TimeUnit.SECONDS), StandardCharsets.UTF_8).trim();
            String stderrText = new String(stderr.get(2, TimeUnit.SECONDS), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0) {
                throw new EngineCommunicationException(
                        "Python engine exited with code " + process.exitValue() + diagnosticSuffix(stderrText)
                );
            }
            if (stdoutText.isEmpty()) {
                throw new EngineCommunicationException("Python engine returned an empty response." + diagnosticSuffix(stderrText));
            }

            EngineResponse response;
            try {
                response = objectMapper.readValue(stdoutText, EngineResponse.class);
            } catch (JsonProcessingException exception) {
                throw new EngineCommunicationException("Python engine returned invalid JSON." + diagnosticSuffix(stderrText), exception);
            }

            if (!PROTOCOL_VERSION.equals(response.protocolVersion())) {
                throw new EngineCommunicationException("Python engine responded with an unsupported protocol version.");
            }
            if (!request.requestId().equals(response.requestId())) {
                throw new EngineCommunicationException("Python engine response request_id does not match the request.");
            }
            return response;
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new EngineCommunicationException("Interrupted while waiting for the Python engine.", exception);
        } catch (ExecutionException | TimeoutException exception) {
            process.destroyForcibly();
            throw new EngineCommunicationException("Could not collect Python engine output.", exception);
        }
    }

    private static CompletableFuture<byte[]> readAsync(InputStream stream) {
        return CompletableFuture.supplyAsync(() -> {
            try (stream) {
                return stream.readAllBytes();
            } catch (IOException exception) {
                throw new java.io.UncheckedIOException(exception);
            }
        });
    }

    private static String diagnosticSuffix(String stderr) {
        return stderr == null || stderr.isBlank() ? "" : " Details: " + stderr;
    }
}
