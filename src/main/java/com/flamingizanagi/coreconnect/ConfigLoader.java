package com.flamingizanagi.coreconnect;

import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Loads config.yml from the plugin data directory into an immutable snapshot.
 */
public final class ConfigLoader {

    private ConfigLoader() {
    }

    public static CoreConnectConfig load(Path dataDirectory, Logger logger) {
        Path configFile = dataDirectory.resolve("config.yml");
        try {
            if (!Files.exists(configFile)) {
                Files.createDirectories(dataDirectory);
                try (InputStream in = ConfigLoader.class.getClassLoader().getResourceAsStream("config.yml")) {
                    if (in == null) {
                        Files.writeString(configFile, fallbackDefaultYaml(), StandardCharsets.UTF_8);
                    } else {
                        Files.copy(in, configFile);
                    }
                }
            }
            return parse(Files.readString(configFile, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            logger.error("[CoreConnect] Failed to load config.yml, using built-in defaults", exception);
            return CoreConnectConfig.defaults();
        }
    }

    static CoreConnectConfig parse(String raw) {
        boolean logRedirections = false;
        boolean rankEnabled = false;
        boolean respectAuth = false;
        Set<String> allowedGroups = new LinkedHashSet<>();
        Map<Integer, String> routes = new LinkedHashMap<>();

        String section = "root";
        for (String originalLine : raw.split("\\R")) {
            String line = stripComment(originalLine).stripTrailing();
            if (line.isBlank()) {
                continue;
            }

            int indent = indentWidth(line);
            String trimmed = line.trim();

            if (indent == 0 && trimmed.endsWith(":") && !trimmed.contains(" ")) {
                String key = trimmed.substring(0, trimmed.length() - 1);
                section = switch (key) {
                    case "rank_restriction" -> "rank_restriction";
                    case "respect_auth" -> "respect_auth";
                    case "server_routes" -> "server_routes";
                    default -> "root";
                };
                continue;
            }

            if ("rank_restriction".equals(section) && indent >= 2 && trimmed.equals("allowed_groups:")) {
                section = "allowed_groups";
                continue;
            }

            if ("allowed_groups".equals(section) && indent >= 4 && trimmed.startsWith("-")) {
                String value = unquote(trimmed.substring(1).trim()).toLowerCase(Locale.ROOT);
                if (!value.isEmpty()) {
                    allowedGroups.add(value);
                }
                continue;
            }

            if ("allowed_groups".equals(section) && indent == 2 && !trimmed.startsWith("-")) {
                section = "rank_restriction";
            }

            if ("server_routes".equals(section) && indent >= 2 && trimmed.contains(":")) {
                int split = trimmed.indexOf(':');
                String key = unquote(trimmed.substring(0, split).trim());
                String value = unquote(trimmed.substring(split + 1).trim());
                try {
                    routes.put(Integer.parseInt(key), value);
                } catch (NumberFormatException ignored) {
                    // Skip invalid route keys.
                }
                continue;
            }

            if (!trimmed.contains(":")) {
                continue;
            }

            int split = trimmed.indexOf(':');
            String key = trimmed.substring(0, split).trim();
            String value = unquote(trimmed.substring(split + 1).trim());

            if ("root".equals(section) && indent == 0 && "log_redirections".equals(key)) {
                logRedirections = Boolean.parseBoolean(value);
            } else if ("rank_restriction".equals(section) && "enabled".equals(key)) {
                rankEnabled = Boolean.parseBoolean(value);
            } else if ("respect_auth".equals(section) && "enabled".equals(key)) {
                respectAuth = Boolean.parseBoolean(value);
            }
        }

        if (allowedGroups.isEmpty()) {
            allowedGroups.add("vip");
            allowedGroups.add("premium");
            allowedGroups.add("owner");
        }
        if (routes.isEmpty()) {
            routes.put(1, "lobby_general");
            routes.put(2, "survival_general");
        }
        return new CoreConnectConfig(
                logRedirections,
                rankEnabled,
                allowedGroups,
                respectAuth,
                routes
        );
    }

    private static String stripComment(String line) {
        boolean inQuotes = false;
        char quote = 0;
        StringBuilder builder = new StringBuilder(line.length());
        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (!inQuotes && current == '#') {
                break;
            }
            if ((current == '"' || current == '\'') && (i == 0 || line.charAt(i - 1) != '\\')) {
                if (!inQuotes) {
                    inQuotes = true;
                    quote = current;
                } else if (current == quote) {
                    inQuotes = false;
                }
            }
            builder.append(current);
        }
        return builder.toString();
    }

    private static int indentWidth(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static String fallbackDefaultYaml() {
        List<String> lines = new ArrayList<>();
        lines.add("# CoreConnect by FlamingIzanagi");
        lines.add("");
        lines.add("# When true, each received route signal is printed as:");
        lines.add("# Redirection ID [id] from (player). Redirecting to \"server\".");
        lines.add("log_redirections: false");
        lines.add("rank_restriction:");
        lines.add("  enabled: false");
        lines.add("  allowed_groups:");
        lines.add("    - \"vip\"");
        lines.add("    - \"premium\"");
        lines.add("    - \"owner\"");
        lines.add("respect_auth:");
        lines.add("  enabled: false");
        lines.add("server_routes:");
        lines.add("  1: \"lobby_general\"");
        lines.add("  2: \"survival_general\"");
        return String.join(System.lineSeparator(), lines);
    }
}
