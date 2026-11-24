package com.netscanner.ui;

/** Compact, display-ready summary of a persisted scan. */
public record ScanHistoryEntry(
        long id,
        String scanType,
        String target,
        String scannedAt,
        long scannedCount,
        long activeCount,
        long openCount,
        long serviceHintCount
) {
    public String typeLabel() {
        return "HOST_DISCOVERY".equals(scanType) ? "Host Discovery" : "TCP Port Scan";
    }

    public String summaryLabel() {
        if ("HOST_DISCOVERY".equals(scanType)) {
            return activeCount + " active / " + scannedCount + " scanned";
        }
        return openCount + " open / " + scannedCount + " scanned";
    }
}
