package com.ella.backend.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimal .env loader for local development.
 *
 * Loads key=value pairs from a ".env" file in the current working directory and sets them as
 * System properties ONLY if the key is not already defined via environment variable or system property.
 *
 * This is intentionally lightweight (no extra dependencies) and safe for production: if the file
 * doesn't exist, it does nothing; and it never overrides existing configuration.
 */
public final class DotenvLoader {

    private static final Logger log = LoggerFactory.getLogger(DotenvLoader.class);

    private DotenvLoader() {
    }

    public static void loadFromWorkingDirectoryIfPresent() {
        List<Path> candidates = new ArrayList<>();
        candidates.add(Path.of(".env"));
        candidates.add(Path.of("backend", ".env"));

        Path envPath = null;
        for (Path candidate : candidates) {
            if (Files.exists(candidate) && Files.isRegularFile(candidate)) {
                envPath = candidate;
                break;
            }
        }

        if (envPath == null) {
            return;
        }

        try {
            List<String> lines = Files.readAllLines(envPath, StandardCharsets.UTF_8);
            int loaded = 0;

            for (String raw : lines) {
                if (raw == null) continue;
                String line = raw.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("#")) continue;

                int idx = line.indexOf('=');
                if (idx <= 0) continue;

                String key = line.substring(0, idx).trim();
                String value = line.substring(idx + 1).trim();

                if (key.isEmpty()) continue;

                if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }

                if (value.isEmpty()) {
                    continue;
                }

                // Do not override existing env var or system property (unless blank).
                String envValue = System.getenv(key);
                if (envValue != null && !envValue.isBlank()) {
                    continue;
                }
                String sysValue = System.getProperty(key);
                if (sysValue != null && !sysValue.isBlank()) {
                    continue;
                }

                System.setProperty(key, value);
                loaded++;
            }

            if (loaded > 0) {
                log.info(
                        "[DotenvLoader] Loaded {} keys from {} into System properties (values hidden)",
                        loaded,
                        envPath.toAbsolutePath()
                );
            }
        } catch (IOException e) {
            log.warn("[DotenvLoader] Failed to read .env (ignored): {}", e.getMessage());
        }
    }
}
