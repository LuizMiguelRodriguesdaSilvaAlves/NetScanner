package com.netscanner.ui;

/** Aggregated values displayed in the dashboard metric cards. */
public record DashboardMetrics(
        long totalScans,
        long activeHosts,
        long openPorts,
        long serviceHints
) {
    public static DashboardMetrics empty() {
        return new DashboardMetrics(0, 0, 0, 0);
    }
}
