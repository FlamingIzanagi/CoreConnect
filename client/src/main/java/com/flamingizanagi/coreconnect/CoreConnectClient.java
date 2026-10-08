package com.flamingizanagi.coreconnect;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-only companion of the CoreConnect Velocity plugin.
 * Developed by FlamingIzanagi.
 */
@Mod(value = CoreConnectClient.MOD_ID, dist = Dist.CLIENT)
@OnlyIn(Dist.CLIENT)
public final class CoreConnectClient {

    public static final String MOD_ID = "coreconnect";
    public static final Logger LOGGER = LoggerFactory.getLogger("CoreConnect");

    public CoreConnectClient(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, CoreConnectConfig.SPEC, "coreconnect-client.toml");
        CoreConnectPayload.bind(CoreConnectConfig.channelLocation());

        modBus.addListener(CoreConnectClient::registerPayloads);
        NeoForge.EVENT_BUS.addListener(ClientPayloadHandler::onLoggingIn);
        NeoForge.EVENT_BUS.addListener(ClientPayloadHandler::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(ClientPayloadHandler::onClientTick);

        LOGGER.info("[CoreConnect] Client mod developed by FlamingIzanagi");
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        if (!CoreConnectPayload.isReady()) {
            CoreConnectPayload.bind(CoreConnectConfig.channelLocation());
        }
        if (!CoreConnectPayload.isReady()) {
            LOGGER.error("[CoreConnect] Channel coreconnect:listening could not be bound. The route ping is disabled.");
            return;
        }

        // Optional: a Paper backend does not speak this channel, and a required payload would drop the connection.
        // Only this id is registered, so other custom payloads stay untouched.
        event.registrar("1")
                .optional()
                .playBidirectional(
                        CoreConnectPayload.payloadType(),
                        CoreConnectPayload.streamCodec(),
                        new DirectionalPayloadHandler<>(
                                ClientPayloadHandler::onInbound,
                                (payload, context) -> {
                                }
                        )
                );

        LOGGER.info("[CoreConnect] Using channel {}", CoreConnectPayload.payloadType().id());
    }
}
