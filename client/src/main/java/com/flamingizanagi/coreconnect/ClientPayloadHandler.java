package com.flamingizanagi.coreconnect;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.lang.ref.WeakReference;

/**
 * Sends the configured route id once per proxy connection.
 * A Velocity backend switch reuses that connection, so the ping is not repeated and the player is not redirected in a loop.
 * The connection is held weakly and cleared on logout. Developed by FlamingIzanagi.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientPayloadHandler {

    private static final int MAX_ATTEMPTS = 100;

    private static boolean pending;
    private static boolean reportedFailure;
    private static boolean reportedWait;
    private static boolean ackExpected;
    private static int attempts;
    private static int sentRouteId;
    private static WeakReference<Connection> sentOn = new WeakReference<>(null);

    private ClientPayloadHandler() {
    }

    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        Connection connection = event.getConnection();
        if (connection != null && connection == sentOn.get()) {
            pending = false;
            return;
        }
        pending = true;
        attempts = 0;
        reportedFailure = false;
        reportedWait = false;
        trySend();
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        pending = false;
        attempts = 0;
        ClientPacketListener listener = Minecraft.getInstance().getConnection();
        if (listener == null || listener.getConnection() != sentOn.get()) {
            sentOn = new WeakReference<>(null);
            ackExpected = false;
        }
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        if (!pending) {
            return;
        }
        if (++attempts > MAX_ATTEMPTS) {
            pending = false;
            if (!CoreConnectConfig.silentPingLog() && CoreConnectPayload.isReady()) {
                CoreConnectClient.LOGGER.warn(
                        "[CoreConnect] Route ping was not sent. Channel {} was not announced by the proxy.",
                        CoreConnectPayload.payloadType().id()
                );
            }
            return;
        }
        trySend();
    }

    /**
     * Runs when the proxy answers on {@code coreconnect:listening}.
     * The answer is the same route id, or that id marked while login is still pending.
     */
    public static void onInbound(CoreConnectPayload payload, IPayloadContext context) {
        if (CoreConnectConfig.silentPingLog() || !ackExpected) {
            return;
        }
        int received = payload.routeId();
        if (received == sentRouteId) {
            CoreConnectClient.LOGGER.info("[CoreConnect] The proxy received the ping for key ID {}", sentRouteId);
            return;
        }
        if ((received ^ CoreConnectPayload.WAITING_MARK) == sentRouteId) {
            CoreConnectClient.LOGGER.info("[CoreConnect] The proxy received the ping. Waiting for the login phase to finish before redirecting the player");
        }
    }

    private static void trySend() {
        if (!CoreConnectPayload.isReady()) {
            return;
        }
        ClientPacketListener listener = Minecraft.getInstance().getConnection();
        if (listener == null) {
            return;
        }
        Connection connection = listener.getConnection();
        if (connection == sentOn.get()) {
            pending = false;
            return;
        }
        if (!listener.hasChannel(CoreConnectPayload.payloadType())) {
            if (!reportedWait) {
                reportedWait = true;
                log("[CoreConnect] Waiting for the login phase to finish before redirecting the player");
            }
            return;
        }

        int routeId = CoreConnectConfig.routeId();
        try {
            listener.send(new CoreConnectPayload(routeId));
        } catch (RuntimeException exception) {
            if (!reportedFailure && !CoreConnectConfig.silentPingLog()) {
                reportedFailure = true;
                CoreConnectClient.LOGGER.warn(
                        "[CoreConnect] Failed to send route {} on {}",
                        routeId,
                        CoreConnectPayload.payloadType().id(),
                        exception
                );
            }
            return;
        }

        sentRouteId = routeId;
        ackExpected = true;
        sentOn = new WeakReference<>(connection);
        pending = false;
        log("[CoreConnect] Sending the key ID {} to allow redirecting the player", routeId);
    }

    private static void log(String message, Object... arguments) {
        if (CoreConnectConfig.silentPingLog()) {
            return;
        }
        CoreConnectClient.LOGGER.info(message, arguments);
    }
}
