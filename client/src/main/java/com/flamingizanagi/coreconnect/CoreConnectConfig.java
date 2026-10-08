package com.flamingizanagi.coreconnect;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client configuration for CoreConnect, by FlamingIzanagi.
 * NeoForge writes this spec to {@code config/coreconnect-client.toml}.
 * {@code silentPingLog} and {@code routeId} are read on each send.
 */
@OnlyIn(Dist.CLIENT)
public final class CoreConnectConfig {

    public static final ResourceLocation CHANNEL = ResourceLocation.parse("coreconnect:listening");

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue SILENT_PING_LOG;
    public static final ModConfigSpec.IntValue ROUTE_ID;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("CoreConnect client. Author: FlamingIzanagi.").push("network");

        SILENT_PING_LOG = builder
                .comment(
                        "Log messages are enabled by default. Set this to true to stop writing them. The route signal is still sent either way."
                )
                .define("silentPingLog", false);

        ROUTE_ID = builder
                .comment("Numeric route id written as a big-endian int. Must match a key under server_routes in the plugin config.")
                .defineInRange("routeId", 1, Integer.MIN_VALUE, Integer.MAX_VALUE);

        builder.pop();
        SPEC = builder.build();
    }

    private CoreConnectConfig() {
    }

    public static ResourceLocation channelLocation() {
        return CHANNEL;
    }

    public static boolean silentPingLog() {
        return SILENT_PING_LOG.get();
    }

    public static int routeId() {
        return ROUTE_ID.get();
    }
}
