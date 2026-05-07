package dev.devix7.ixskins.client;

import dev.devix7.ixskins.IxSkinsSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class IxSkinsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(IxSkinsSyncPayload.ID, (payload, context) -> {
            context.client().execute(() -> ClientSkinRegistry.applySnapshot(payload.snapshot()));
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientSkinRegistry.clearAll());
    }
}
