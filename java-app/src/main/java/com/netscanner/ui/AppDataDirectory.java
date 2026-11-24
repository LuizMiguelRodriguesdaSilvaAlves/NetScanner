package com.netscanner.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Resolves a per-user data directory without writing database files into the repository. */
public final class AppDataDirectory {
    private AppDataDirectory() {
    }

    public static Path databasePath() throws IOException {
        String os = System.getProperty("os.name", "").toLowerCase();
        Path directory;
        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            directory = localAppData == null || localAppData.isBlank()
                    ? Path.of(System.getProperty("user.home"), "AppData", "Local", "NetScanner")
                    : Path.of(localAppData, "NetScanner");
        } else {
            String xdgDataHome = System.getenv("XDG_DATA_HOME");
            directory = xdgDataHome == null || xdgDataHome.isBlank()
                    ? Path.of(System.getProperty("user.home"), ".local", "share", "netscanner")
                    : Path.of(xdgDataHome, "netscanner");
        }
        Files.createDirectories(directory);
        return directory.resolve("netscanner.db");
    }
}
