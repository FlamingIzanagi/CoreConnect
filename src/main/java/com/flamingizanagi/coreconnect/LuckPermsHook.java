package com.flamingizanagi.coreconnect;

import com.velocitypowered.api.proxy.Player;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.slf4j.Logger;

import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Non-blocking LuckPerms group checks. Loaded only when LuckPerms is present.
 */
public final class LuckPermsHook {

    private final Logger logger;
    private final LuckPerms luckPerms;

    public LuckPermsHook(Logger logger) {
        this.logger = logger;
        this.luckPerms = LuckPermsProvider.get();
    }

    public void hasAllowedGroup(Player player, Set<String> allowedGroups, Consumer<Boolean> callback) {
        luckPerms.getUserManager().loadUser(player.getUniqueId()).whenComplete((user, throwable) -> {
            if (throwable != null) {
                logger.error("[CoreConnect] Failed to load LuckPerms user {}", player.getUsername(), throwable);
                callback.accept(false);
                return;
            }
            callback.accept(matches(user, allowedGroups));
        });
    }

    private boolean matches(User user, Set<String> allowedGroups) {
        if (user == null) {
            return false;
        }
        if (allowedGroups.contains(user.getPrimaryGroup().toLowerCase(Locale.ROOT))) {
            return true;
        }
        var permissionData = user.getCachedData().getPermissionData();
        for (String group : allowedGroups) {
            if (permissionData.checkPermission("group." + group).asBoolean()) {
                return true;
            }
        }
        return false;
    }
}
