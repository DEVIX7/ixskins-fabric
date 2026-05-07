package dev.devix7.ixskins;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IxSkins implements ModInitializer {
    public static final String MOD_ID = "ixskins";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ServerSkinState.load();
        IxSkinsSyncPayload.registerPayload();
        IxSkinsCommands.register();

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ServerSkinState.sendTo(handler.getPlayer()));
    }
}
