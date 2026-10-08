package com.flamingizanagi.coreconnect;

import java.util.Map;
import java.util.Set;

/**
 * Immutable snapshot of config.yml, loaded into RAM on startup.
 */
public final class CoreConnectConfig {

    private final boolean logRedirections;
    private final boolean rankRestrictionEnabled;
    private final Set<String> allowedGroups;
    private final boolean respectAuthEnabled;
    private final Map<Integer, String> serverRoutes;

    public CoreConnectConfig(
            boolean logRedirections,
            boolean rankRestrictionEnabled,
            Set<String> allowedGroups,
            boolean respectAuthEnabled,
            Map<Integer, String> serverRoutes
    ) {
        this.logRedirections = logRedirections;
        this.rankRestrictionEnabled = rankRestrictionEnabled;
        this.allowedGroups = Set.copyOf(allowedGroups);
        this.respectAuthEnabled = respectAuthEnabled;
        this.serverRoutes = Map.copyOf(serverRoutes);
    }

    public boolean logRedirections() {
        return logRedirections;
    }

    public boolean rankRestrictionEnabled() {
        return rankRestrictionEnabled;
    }

    public Set<String> allowedGroups() {
        return allowedGroups;
    }

    public boolean respectAuthEnabled() {
        return respectAuthEnabled;
    }

    public Map<Integer, String> serverRoutes() {
        return serverRoutes;
    }

    public static CoreConnectConfig defaults() {
        return new CoreConnectConfig(
                false,
                false,
                Set.of("vip", "premium", "owner"),
                false,
                Map.of(1, "lobby_general", 2, "survival_general")
        );
    }
}
