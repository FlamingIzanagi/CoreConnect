package com.flamingizanagi.coreconnect;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * One big-endian route id on {@code coreconnect:listening}.
 * The body is a single {@link ByteBufCodecs#INT}, which matches
 * {@code PluginMessageEvent.dataAsDataStream().readInt()} on the Velocity plugin.
 * Developed by FlamingIzanagi.
 */
@OnlyIn(Dist.CLIENT)
public record CoreConnectPayload(int routeId) implements CustomPacketPayload {

    /**
     * XORed into the route id when the proxy accepts the ping but holds the redirect until login finishes.
     * The plugin must use the same mark.
     */
    public static final int WAITING_MARK = 1 << 30;

    private static Type<CoreConnectPayload> payloadType;
    private static StreamCodec<ByteBuf, CoreConnectPayload> streamCodec;

    /**
     * Binds this payload to {@code coreconnect:listening}. A second call is ignored so the codec is never replaced.
     *
     * @return {@code false} when {@code channel} is missing
     */
    public static boolean bind(ResourceLocation channel) {
        if (channel == null) {
            return false;
        }
        if (payloadType != null) {
            return true;
        }
        payloadType = new Type<>(channel);
        streamCodec = StreamCodec.composite(
                ByteBufCodecs.INT,
                CoreConnectPayload::routeId,
                CoreConnectPayload::new
        );
        return true;
    }

    public static boolean isReady() {
        return payloadType != null;
    }

    public static Type<CoreConnectPayload> payloadType() {
        return payloadType;
    }

    public static StreamCodec<ByteBuf, CoreConnectPayload> streamCodec() {
        return streamCodec;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return payloadType;
    }
}
