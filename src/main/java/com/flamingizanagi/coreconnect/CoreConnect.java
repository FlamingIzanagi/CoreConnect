package com.flamingizanagi.coreconnect;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Plugin(
        id = "coreconnect",
        name = "CoreConnect",
        version = "1.0.0",
        authors = {"FlamingIzanagi"},
        description = "Redirects players from a client-mod custom payload using in-memory routes.",
        dependencies = {
                @Dependency(id = "luckperms", optional = true),
                @Dependency(id = "nlogin", optional = true)
        }
)
public final class CoreConnect {

    public static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from("coreconnect:listening");

    /** Must match {@code CoreConnectPayload.WAITING_MARK} on the client mod. */
    private static final int WAITING_MARK = 1 << 30;

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;

    private final ConcurrentHashMap<UUID, String> pendingRoutes = new ConcurrentHashMap<>();

    private volatile CoreConnectConfig config;
    private volatile ChannelIdentifier channel;
    private volatile LuckPermsHook luckPermsHook;
    private volatile boolean nLoginRegistered;

    @Inject
    public CoreConnect(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        try {
            CoreConnectConfig loaded = ConfigLoader.load(dataDirectory, logger);
            logger.info("[CoreConnect] Developed by FlamingIzanagi");
            applyConfiguration(null, loaded);

            proxy.getCommandManager().register(
                    proxy.getCommandManager().metaBuilder("coreconnect").plugin(this).build(),
                    new CoreConnectCommand(this)
            );

            if (config == null || channel == null) {
                logger.error("[CoreConnect] Failed to enable. Check config.yml.");
                return;
            }
            logger.info("[CoreConnect] Enabled successfully.");
        } catch (Exception exception) {
            logger.error("[CoreConnect] Failed to enable.", exception);
        }
    }

    void reloadConfiguration(CommandSource source) {
        CoreConnectConfig previous = this.config;
        CoreConnectConfig loaded = ConfigLoader.load(dataDirectory, logger);
        applyConfiguration(previous, loaded);
        source.sendMessage(Component.text("[CoreConnect] Configuration reloaded.", NamedTextColor.GREEN));
        logger.info("[CoreConnect] Configuration reloaded");
    }

    private void applyConfiguration(CoreConnectConfig previous, CoreConnectConfig next) {
        registerChannel();
        this.config = next;
        ensureLuckPerms(next);
        if (next.respectAuthEnabled()) {
            if (registerNLoginListener() && (previous == null || !previous.respectAuthEnabled())) {
                logger.info("[CoreConnect] nLogin auth routing enabled");
            } else if (!nLoginRegistered) {
                logger.warn("[CoreConnect] respect_auth is enabled but the nLogin API was not found. Pending tickets will not complete.");
            }
        }
    }

    private void registerChannel() {
        if (this.channel != null) {
            return;
        }
        proxy.getChannelRegistrar().register(CHANNEL);
        this.channel = CHANNEL;
    }

    private void ensureLuckPerms(CoreConnectConfig snapshot) {
        if (luckPermsHook != null) {
            return;
        }
        if (isPluginPresent("luckperms")) {
            try {
                this.luckPermsHook = new LuckPermsHook(logger);
                logger.info("[CoreConnect] LuckPerms hook enabled");
            } catch (IllegalStateException exception) {
                logger.warn("[CoreConnect] LuckPerms is installed but the API is not ready");
            }
            return;
        }
        if (snapshot.rankRestrictionEnabled()) {
            logger.warn("[CoreConnect] rank_restriction is enabled but LuckPerms was not found. Redirections will be skipped.");
        }
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        CoreConnectConfig snapshot = this.config;
        ChannelIdentifier registered = this.channel;
        if (snapshot == null || registered == null) {
            return;
        }
        if (!registered.equals(event.getIdentifier())) {
            return;
        }

        event.setResult(PluginMessageEvent.ForwardResult.handled());

        if (!(event.getSource() instanceof Player player)) {
            return;
        }

        byte[] data = event.getData();
        if (data.length < Integer.BYTES) {
            return;
        }

        int routeId;
        try {
            routeId = event.dataAsDataStream().readInt();
        } catch (Exception exception) {
            return;
        }

        String serverName = snapshot.serverRoutes().get(routeId);
        if (serverName == null) {
            return;
        }

        if (snapshot.rankRestrictionEnabled()) {
            LuckPermsHook hook = this.luckPermsHook;
            if (hook == null) {
                return;
            }
            hook.hasAllowedGroup(player, snapshot.allowedGroups(), allowed -> {
                if (!allowed) {
                    return;
                }
                applyAuthRouting(player, routeId, serverName, snapshot);
            });
            return;
        }

        applyAuthRouting(player, routeId, serverName, snapshot);
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        pendingRoutes.remove(event.getPlayer().getUniqueId());
    }

    void completePendingRoute(Player player) {
        String serverName = pendingRoutes.remove(player.getUniqueId());
        if (serverName == null) {
            return;
        }
        connect(player, serverName);
    }

    private void applyAuthRouting(Player player, int routeId, String serverName, CoreConnectConfig snapshot) {
        if (!player.isActive()) {
            return;
        }
        if (snapshot.logRedirections()) {
            logger.info("[CoreConnect] Redirection ID [{}] from ({}). Redirecting to \"{}\".", routeId, player.getUsername(), serverName);
        }
        if (!snapshot.respectAuthEnabled()) {
            acknowledge(player, routeId, false, () -> connect(player, serverName));
            return;
        }
        pendingRoutes.put(player.getUniqueId(), serverName);
        acknowledge(player, routeId, true, null);
    }

    private void acknowledge(Player player, int routeId, boolean waitingForLogin, Runnable afterSent) {
        int signal = waitingForLogin ? (routeId ^ WAITING_MARK) : routeId;
        byte[] body = ByteBuffer.allocate(Integer.BYTES).putInt(signal).array();
        sendAck(player, routeId, body, 0, afterSent);
    }

    private void sendAck(Player player, int routeId, byte[] body, int attempt, Runnable afterSent) {
        if (!player.isActive()) {
            return;
        }
        boolean sent = false;
        try {
            sent = player.sendPluginMessage(CHANNEL, body);
        } catch (Exception exception) {
            logger.warn("[CoreConnect] Could not acknowledge route {} for {}", routeId, player.getUsername(), exception);
            if (afterSent != null) {
                afterSent.run();
            }
            return;
        }
        if (sent) {
            if (afterSent != null) {
                proxy.getScheduler().buildTask(this, afterSent).delay(Duration.ofMillis(50)).schedule();
            }
            return;
        }
        if (attempt >= 20) {
            logger.warn("[CoreConnect] Could not acknowledge route {} for {}", routeId, player.getUsername());
            if (afterSent != null) {
                afterSent.run();
            }
            return;
        }
        proxy.getScheduler()
                .buildTask(this, () -> sendAck(player, routeId, body, attempt + 1, afterSent))
                .delay(Duration.ofMillis(50))
                .schedule();
    }

    private void connect(Player player, String serverName) {
        if (!player.isActive()) {
            return;
        }
        Optional<RegisteredServer> target = proxy.getServer(serverName);
        if (target.isEmpty()) {
            logger.warn("[CoreConnect] Target server '{}' is not registered on this proxy", serverName);
            return;
        }
        player.createConnectionRequest(target.get()).fireAndForget();
    }

    private boolean registerNLoginListener() {
        if (nLoginRegistered) {
            return true;
        }
        try {
            Class.forName("com.nickuc.login.api.event.velocity.auth.LoginEvent");
            proxy.getEventManager().register(this, new NLoginAuthListener(this));
            nLoginRegistered = true;
            return true;
        } catch (ClassNotFoundException | NoClassDefFoundError exception) {
            logger.warn("[CoreConnect] nLogin API classes were not found on the classpath");
            return false;
        }
    }

    private boolean isPluginPresent(String id) {
        return proxy.getPluginManager().getPlugin(id).isPresent();
    }
}
