package com.netscanner.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.netscanner.engine.PythonEngineClient;
import com.netscanner.model.EngineResponse;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/** Single-page dashboard for the currently implemented NetScanner operations. */
public final class DashboardView extends BorderPane {
    private final PythonEngineClient engineClient;
    private final ScanHistoryRepository historyRepository;
    private final ExecutorService backgroundExecutor;

    private final Label engineStatus = new Label("Checking engine...");
    private final Label statusMessage = new Label("Ready. Scan only networks you own or are authorized to test.");
    private final ProgressIndicator progressIndicator = new ProgressIndicator();

    private final Label scansValue = new Label("0");
    private final Label hostsValue = new Label("0");
    private final Label portsValue = new Label("0");
    private final Label hintsValue = new Label("0");

    private final TextField cidrField = new TextField("127.0.0.1/32");
    private final Spinner<Integer> discoveryTimeout = spinner(100, 3000, 500, 50);
    private final Spinner<Integer> discoveryWorkers = spinner(1, 128, 64, 1);

    private final TextField targetField = new TextField("127.0.0.1");
    private final TextField portsField = new TextField("22,80,443,8080");
    private final Spinner<Integer> scanTimeout = spinner(100, 3000, 500, 50);
    private final Spinner<Integer> scanWorkers = spinner(1, 128, 64, 1);

    private final Button discoveryButton = new Button("Run discovery");
    private final Button scanButton = new Button("Start TCP scan");
    private final TableView<ScanHistoryEntry> historyTable = new TableView<>();
    private final TableView<ScanResultRow> resultsTable = new TableView<>();

    public DashboardView(
            PythonEngineClient engineClient,
            ScanHistoryRepository historyRepository,
            ExecutorService backgroundExecutor
    ) {
        this.engineClient = engineClient;
        this.historyRepository = historyRepository;
        this.backgroundExecutor = backgroundExecutor;
        setLeft(buildSidebar());
        setCenter(buildDashboardContent());
        progressIndicator.setMaxSize(18, 18);
        progressIndicator.setVisible(false);
        refreshDashboard();
    }

    public void checkEngine() {
        Task<EngineResponse> task = new Task<>() {
            @Override
            protected EngineResponse call() {
                return engineClient.ping();
            }
        };
        task.setOnSucceeded(event -> {
            EngineResponse response = task.getValue();
            boolean ready = response != null && response.success();
            engineStatus.setText(ready ? "ENGINE ONLINE" : "ENGINE UNAVAILABLE");
            engineStatus.getStyleClass().removeAll("status-online", "status-offline");
            engineStatus.getStyleClass().add(ready ? "status-online" : "status-offline");
        });
        task.setOnFailed(event -> {
            engineStatus.setText("ENGINE UNAVAILABLE");
            engineStatus.getStyleClass().remove("status-online");
            if (!engineStatus.getStyleClass().contains("status-offline")) {
                engineStatus.getStyleClass().add("status-offline");
            }
        });
        backgroundExecutor.submit(task);
    }

    private Parent buildSidebar() {
        VBox sidebar = new VBox(18);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPadding(new Insets(28, 18, 22, 18));
        sidebar.setPrefWidth(246);

        HBox brand = new HBox(12);
        brand.setAlignment(Pos.CENTER_LEFT);
        Label mark = new Label("N");
        mark.getStyleClass().add("brand-mark");
        VBox brandText = new VBox(2);
        Label name = new Label("NetScanner");
        name.getStyleClass().add("brand-name");
        Label descriptor = new Label("NETWORK ANALYSIS");
        descriptor.getStyleClass().add("brand-caption");
        brandText.getChildren().addAll(name, descriptor);
        brand.getChildren().addAll(mark, brandText);

        Label mainSection = new Label("WORKSPACE");
        mainSection.getStyleClass().add("nav-section");
        VBox nav = new VBox(7,
                navigationItem("▦", "Dashboard", true, false),
                navigationItem("◷", "Scan Profiles", false, true),
                navigationItem("▤", "Reports", false, true),
                navigationItem("⇄", "Compare Scans", false, true)
        );

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        VBox footer = new VBox(6);
        Label version = new Label("NETSCANNER  v0.5.0");
        version.getStyleClass().add("sidebar-version");
        Label license = new Label("Apache License 2.0");
        license.getStyleClass().add("sidebar-caption");
        footer.getChildren().addAll(version, license);

        sidebar.getChildren().addAll(brand, mainSection, nav, spacer, footer);
        return sidebar;
    }

    private HBox navigationItem(String symbol, String title, boolean active, boolean workInProgress) {
        HBox item = new HBox(12);
        item.setAlignment(Pos.CENTER_LEFT);
        item.getStyleClass().add("nav-item");
        if (active) {
            item.getStyleClass().add("nav-item-active");
        }
        Label icon = new Label(symbol);
        icon.getStyleClass().add("nav-icon");
        Label text = new Label(title);
        text.getStyleClass().add("nav-text");
        item.getChildren().addAll(icon, text);
        if (workInProgress) {
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Label badge = new Label("W.I.P.");
            badge.getStyleClass().add("work-in-progress");
            item.getChildren().addAll(spacer, badge);
        }
        return item;
    }

    private Parent buildDashboardContent() {
        VBox content = new VBox(22);
        content.getStyleClass().add("dashboard-content");
        content.setPadding(new Insets(30, 34, 34, 34));

        HBox heading = new HBox(18);
        heading.setAlignment(Pos.CENTER_LEFT);
        VBox titleBlock = new VBox(5);
        Label title = new Label("Network overview");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Discover hosts, scan TCP ports and review service hints.");
        subtitle.getStyleClass().add("page-subtitle");
        titleBlock.getChildren().addAll(title, subtitle);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        engineStatus.getStyleClass().addAll("status-pill", "status-offline");
        heading.getChildren().addAll(titleBlock, spacer, engineStatus);

        HBox metrics = new HBox(14,
                metricCard("TOTAL SCANS", scansValue, "Saved in local history", "◷"),
                metricCard("ACTIVE HOSTS", hostsValue, "From latest discovery", "⌘"),
                metricCard("OPEN PORTS", portsValue, "From latest TCP scan", "↗"),
                metricCard("SERVICE HINTS", hintsValue, "Known TCP port mappings", "◇")
        );
        for (var node : metrics.getChildren()) {
            HBox.setHgrow(node, Priority.ALWAYS);
        }

        Label operationsTitle = sectionTitle("Scan operations");
        Label operationsSubtitle = mutedLabel("Use only private IPv4 ranges, loopback or systems you are authorized to assess.");
        HBox operationsHeading = new HBox(12, operationsTitle, operationsSubtitle);
        operationsHeading.setAlignment(Pos.BASELINE_LEFT);
        HBox operationCards = new HBox(16, buildDiscoveryCard(), buildPortScanCard());
        for (var node : operationCards.getChildren()) {
            HBox.setHgrow(node, Priority.ALWAYS);
        }

        VBox activityCard = new VBox(12);
        activityCard.getStyleClass().add("panel");
        HBox activityHeading = new HBox(12);
        activityHeading.setAlignment(Pos.CENTER_LEFT);
        Label activityTitle = sectionTitle("Recent scans");
        Region activitySpacer = new Region();
        HBox.setHgrow(activitySpacer, Priority.ALWAYS);
        Label localStorage = new Label("LOCAL SQLITE");
        localStorage.getStyleClass().add("subtle-badge");
        activityHeading.getChildren().addAll(activityTitle, activitySpacer, localStorage);
        configureHistoryTable();
        activityCard.getChildren().addAll(activityHeading, historyTable);

        VBox resultsCard = new VBox(12);
        resultsCard.getStyleClass().add("panel");
        HBox resultsHeading = new HBox(12);
        resultsHeading.setAlignment(Pos.CENTER_LEFT);
        Label resultsTitle = sectionTitle("Latest scan results");
        Region resultsSpacer = new Region();
        HBox.setHgrow(resultsSpacer, Priority.ALWAYS);
        resultsHeading.getChildren().addAll(resultsTitle, resultsSpacer, progressIndicator);
        configureResultsTable();
        resultsCard.getChildren().addAll(resultsHeading, resultsTable);

        HBox statusRow = new HBox(statusMessage);
        statusRow.getStyleClass().add("status-message");
        statusRow.setPadding(new Insets(8, 2, 0, 2));

        content.getChildren().addAll(heading, metrics, operationsHeading, operationCards,
                activityCard, resultsCard, statusRow);

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("dashboard-scroll");
        return scrollPane;
    }

    private VBox metricCard(String title, Label value, String description, String symbol) {
        VBox card = new VBox(9);
        card.getStyleClass().add("metric-card");
        HBox top = new HBox(8);
        top.setAlignment(Pos.CENTER_LEFT);
        Label heading = new Label(title);
        heading.getStyleClass().add("metric-heading");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label icon = new Label(symbol);
        icon.getStyleClass().add("metric-icon");
        top.getChildren().addAll(heading, spacer, icon);
        value.getStyleClass().add("metric-value");
        Label caption = mutedLabel(description);
        card.getChildren().addAll(top, value, caption);
        return card;
    }

    private VBox buildDiscoveryCard() {
        VBox card = new VBox(14);
        card.getStyleClass().add("panel");
        Label title = sectionTitle("Host Discovery");
        Label description = mutedLabel("Find responsive hosts in an authorized IPv4 CIDR range.");
        cidrField.setPromptText("192.168.1.0/24");
        HBox options = new HBox(12,
                labeledControl("TIMEOUT (MS)", discoveryTimeout),
                labeledControl("MAX WORKERS", discoveryWorkers));
        discoveryButton.getStyleClass().add("primary-button");
        discoveryButton.setMaxWidth(Double.MAX_VALUE);
        discoveryButton.setOnAction(event -> runDiscovery());
        card.getChildren().addAll(title, description, labeledControl("IPV4 CIDR", cidrField),
                options, discoveryButton);
        return card;
    }

    private VBox buildPortScanCard() {
        VBox card = new VBox(14);
        card.getStyleClass().add("panel");
        Label title = sectionTitle("TCP Port Scan");
        Label description = mutedLabel("Check selected TCP ports on one private IPv4 address.");
        targetField.setPromptText("127.0.0.1");
        portsField.setPromptText("22,80,443,8000-8100");
        HBox options = new HBox(12,
                labeledControl("TIMEOUT (MS)", scanTimeout),
                labeledControl("MAX WORKERS", scanWorkers));
        scanButton.getStyleClass().add("primary-button");
        scanButton.setMaxWidth(Double.MAX_VALUE);
        scanButton.setOnAction(event -> runPortScan());
        card.getChildren().addAll(title, description,
                labeledControl("TARGET IPV4", targetField),
                labeledControl("TCP PORTS", portsField),
                options, scanButton);
        return card;
    }

    private VBox labeledControl(String label, javafx.scene.Node control) {
        VBox box = new VBox(6);
        Label caption = new Label(label);
        caption.getStyleClass().add("field-caption");
        control.getStyleClass().add("field-control");
        box.getChildren().addAll(caption, control);
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    private void configureHistoryTable() {
        TableColumn<ScanHistoryEntry, String> dateColumn = new TableColumn<>("DATE");
        dateColumn.setCellValueFactory(cell -> new javafx.beans.property.ReadOnlyStringWrapper(
                cell.getValue().scannedAt().replace("T", " ").replace("Z", "")));
        TableColumn<ScanHistoryEntry, String> typeColumn = new TableColumn<>("OPERATION");
        typeColumn.setCellValueFactory(cell -> new javafx.beans.property.ReadOnlyStringWrapper(
                cell.getValue().typeLabel()));
        TableColumn<ScanHistoryEntry, String> targetColumn = new TableColumn<>("TARGET");
        targetColumn.setCellValueFactory(cell -> new javafx.beans.property.ReadOnlyStringWrapper(
                cell.getValue().target()));
        TableColumn<ScanHistoryEntry, String> summaryColumn = new TableColumn<>("SUMMARY");
        summaryColumn.setCellValueFactory(cell -> new javafx.beans.property.ReadOnlyStringWrapper(
                cell.getValue().summaryLabel()));
        historyTable.getColumns().setAll(dateColumn, typeColumn, targetColumn, summaryColumn);
        historyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        historyTable.setItems(FXCollections.observableArrayList());
        historyTable.setPlaceholder(new Label("No scans saved yet. Run a discovery or TCP scan to begin."));
        historyTable.setPrefHeight(205);
        historyTable.getStyleClass().add("data-table");
    }

    private void configureResultsTable() {
        TableColumn<ScanResultRow, String> targetColumn = new TableColumn<>("HOST / PORT");
        targetColumn.setCellValueFactory(cell -> new javafx.beans.property.ReadOnlyStringWrapper(
                cell.getValue().target()));
        TableColumn<ScanResultRow, String> typeColumn = new TableColumn<>("TYPE");
        typeColumn.setCellValueFactory(cell -> new javafx.beans.property.ReadOnlyStringWrapper(
                cell.getValue().type()));
        TableColumn<ScanResultRow, String> stateColumn = new TableColumn<>("STATE");
        stateColumn.setCellValueFactory(cell -> new javafx.beans.property.ReadOnlyStringWrapper(
                cell.getValue().state()));
        TableColumn<ScanResultRow, String> detailColumn = new TableColumn<>("SERVICE / DETAILS");
        detailColumn.setCellValueFactory(cell -> new javafx.beans.property.ReadOnlyStringWrapper(
                cell.getValue().detail()));
        TableColumn<ScanResultRow, String> latencyColumn = new TableColumn<>("LATENCY");
        latencyColumn.setCellValueFactory(cell -> new javafx.beans.property.ReadOnlyStringWrapper(
                cell.getValue().latency()));
        resultsTable.getColumns().setAll(targetColumn, typeColumn, stateColumn, detailColumn, latencyColumn);
        resultsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        resultsTable.setItems(FXCollections.observableArrayList());
        resultsTable.setPlaceholder(new Label("Results from your latest scan will appear here."));
        resultsTable.setPrefHeight(230);
        resultsTable.getStyleClass().add("data-table");
    }

    private void runDiscovery() {
        String cidr = cidrField.getText() == null ? "" : cidrField.getText().trim();
        if (cidr.isEmpty()) {
            setStatus("Enter an IPv4 CIDR range before starting discovery.", false);
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("cidr", cidr);
        payload.put("timeout_ms", discoveryTimeout.getValue());
        payload.put("max_workers", discoveryWorkers.getValue());
        executeScan("HOST_DISCOVERY", cidr, "network.host_discovery", payload);
    }

    private void runPortScan() {
        String target = targetField.getText() == null ? "" : targetField.getText().trim();
        String ports = portsField.getText() == null ? "" : portsField.getText().trim();
        if (target.isEmpty() || ports.isEmpty()) {
            setStatus("Enter a target IPv4 address and at least one TCP port or range.", false);
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("target", target);
        payload.put("ports", ports);
        payload.put("timeout_ms", scanTimeout.getValue());
        payload.put("max_workers", scanWorkers.getValue());
        executeScan("TCP_PORT_SCAN", target, "network.port_scan", payload);
    }

    private void executeScan(String scanType, String target, String action, Map<String, Object> payload) {
        setBusy(true);
        setStatus("Running " + ("HOST_DISCOVERY".equals(scanType) ? "Host Discovery" : "TCP Port Scan") + "...", true);

        Task<EngineResponse> task = new Task<>() {
            @Override
            protected EngineResponse call() throws Exception {
                EngineResponse response = engineClient.execute(action, payload);
                if (response.success() && response.data() != null) {
                    historyRepository.saveScan(scanType, target, response.data());
                }
                return response;
            }
        };
        task.setOnSucceeded(event -> {
            setBusy(false);
            EngineResponse response = task.getValue();
            if (!response.success()) {
                String message = response.error() == null
                        ? "The Python engine rejected the scan request."
                        : response.error().code() + ": " + response.error().message();
                setStatus(message, false);
                return;
            }
            showScanResults(scanType, target, response.data());
            refreshDashboard();
            setStatus("Scan completed successfully. Results were saved to local history.", true);
        });
        task.setOnFailed(event -> {
            setBusy(false);
            Throwable error = task.getException();
            setStatus(error == null || error.getMessage() == null
                    ? "The scan could not be completed."
                    : error.getMessage(), false);
        });
        backgroundExecutor.submit(task);
    }

    private void showScanResults(String scanType, String target, JsonNode data) {
        List<ScanResultRow> rows = new ArrayList<>();
        if ("HOST_DISCOVERY".equals(scanType)) {
            JsonNode hosts = data.path("hosts");
            if (hosts.isArray()) {
                for (JsonNode host : hosts) {
                    String ip = host.path("ip").asText("unknown");
                    rows.add(new ScanResultRow(
                            ip,
                            "Host",
                            "ACTIVE",
                            "Open ports: " + formatOpenPorts(host.path("open_ports")),
                            formatLatency(host.path("latency_ms"))
                    ));
                }
            }
        } else {
            JsonNode results = data.path("results");
            if (results.isArray()) {
                for (JsonNode result : results) {
                    String service = result.path("service_hint").asText("");
                    rows.add(new ScanResultRow(
                            target + ":" + result.path("port").asText("?"),
                            "TCP",
                            result.path("state").asText("unknown").toUpperCase(),
                            service.isBlank() ? "No service hint" : service,
                            formatLatency(result.path("latency_ms"))
                    ));
                }
            }
        }
        resultsTable.setItems(FXCollections.observableArrayList(rows));
        if (rows.isEmpty()) {
            resultsTable.setPlaceholder(new Label("The scan completed, but no responsive hosts or port results were returned."));
        }
    }

    private static String formatOpenPorts(JsonNode ports) {
        if (!ports.isArray() || ports.isEmpty()) {
            return "No open ports in discovery probe set";
        }
        List<String> values = new ArrayList<>();
        ports.forEach(port -> values.add(port.asText()));
        return String.join(", ", values);
    }

    private static String formatLatency(JsonNode latency) {
        return latency.isNumber() ? String.format("%.2f ms", latency.asDouble()) : "—";
    }

    private void refreshDashboard() {
        try {
            DashboardMetrics metrics = historyRepository.loadDashboardMetrics();
            scansValue.setText(Long.toString(metrics.totalScans()));
            hostsValue.setText(Long.toString(metrics.activeHosts()));
            portsValue.setText(Long.toString(metrics.openPorts()));
            hintsValue.setText(Long.toString(metrics.serviceHints()));
            historyTable.setItems(FXCollections.observableArrayList(historyRepository.findRecent(8)));
        } catch (SQLException exception) {
            setStatus("Could not load local scan history: " + exception.getMessage(), false);
        }
    }

    private void setBusy(boolean busy) {
        discoveryButton.setDisable(busy);
        scanButton.setDisable(busy);
        progressIndicator.setVisible(busy);
    }

    private void setStatus(String message, boolean success) {
        statusMessage.setText(message);
        statusMessage.getStyleClass().removeAll("status-success", "status-error");
        statusMessage.getStyleClass().add(success ? "status-success" : "status-error");
    }

    private static Label sectionTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }

    private static Label mutedLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("muted-text");
        label.setWrapText(true);
        return label;
    }

    private static Spinner<Integer> spinner(int min, int max, int initial, int step) {
        Spinner<Integer> spinner = new Spinner<>();
        spinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, initial, step));
        spinner.setEditable(true);
        spinner.setPrefWidth(125);
        return spinner;
    }

    private record ScanResultRow(String target, String type, String state, String detail, String latency) {
    }
}
