package com.netscanner.ui;

import com.netscanner.config.EngineConfiguration;
import com.netscanner.engine.PythonEngineClient;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** JavaFX entry point for the NetScanner dashboard. */
public final class DashboardApplication extends Application {
    private ExecutorService backgroundExecutor;

    @Override
    public void start(Stage stage) {
        try {
            EngineConfiguration configuration = EngineConfiguration.fromEnvironment();
            PythonEngineClient engineClient = new PythonEngineClient(
                    configuration.pythonExecutable(),
                    configuration.engineDirectory(),
                    configuration.timeout()
            );
            ScanHistoryRepository historyRepository =
                    new ScanHistoryRepository(AppDataDirectory.databasePath());
            backgroundExecutor = Executors.newFixedThreadPool(2, runnable -> {
                Thread thread = new Thread(runnable, "netscanner-background");
                thread.setDaemon(true);
                return thread;
            });

            DashboardView dashboard = new DashboardView(engineClient, historyRepository, backgroundExecutor);
            Scene scene = new Scene(dashboard, 1440, 960);
            var stylesheet = getClass().getResource("/styles/dashboard.css");
            if (stylesheet != null) {
                scene.getStylesheets().add(stylesheet.toExternalForm());
            }

            stage.setTitle("NetScanner | Network Reconnaissance & Analysis");
            stage.setMinWidth(1080);
            stage.setMinHeight(720);
            stage.setScene(scene);
            stage.show();
            dashboard.checkEngine();
        } catch (Exception exception) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("NetScanner could not start");
            alert.setHeaderText("The dashboard initialization failed.");
            alert.setContentText(exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage());
            alert.showAndWait();
            Platform.exit();
        }
    }

    @Override
    public void stop() {
        if (backgroundExecutor != null) {
            backgroundExecutor.shutdownNow();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
