package dev.devix7.ixskins;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerSkinState {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path SAVE_FILE = FabricLoader.getInstance().getConfigDir().resolve("ixskins/skins.json");
    private static final Map<UUID, SkinSource> SKINS = new ConcurrentHashMap<>();

    private ServerSkinState() {
    }

    public static void set(UUID uuid, SkinSource source) {
        SKINS.put(uuid, source);
        save();
    }

    public static void clear(UUID uuid) {
        SKINS.remove(uuid);
        save();
    }

    public static Map<UUID, SkinSource> snapshot() {
        return Map.copyOf(SKINS);
    }

    public static void load() {
        SKINS.clear();

        if (!Files.exists(SAVE_FILE)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(SAVE_FILE, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                UUID uuid = UUID.fromString(entry.getKey());
                JsonObject object = entry.getValue().getAsJsonObject();
                SkinSource.Kind kind = SkinSource.Kind.valueOf(object.get("type").getAsString());
                String value = object.get("value").getAsString();
                SKINS.put(uuid, new SkinSource(kind, value));
            }
        } catch (Exception exception) {
            IxSkins.LOGGER.warn("Could not load IX Skins config from {}", SAVE_FILE, exception);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(SAVE_FILE.getParent());

            JsonObject root = new JsonObject();
            for (Map.Entry<UUID, SkinSource> entry : SKINS.entrySet()) {
                SkinSource source = entry.getValue();
                JsonObject object = new JsonObject();
                object.addProperty("type", source.kind().name());
                object.addProperty("value", source.value());
                root.add(entry.getKey().toString(), object);
            }

            try (Writer writer = Files.newBufferedWriter(SAVE_FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception exception) {
            IxSkins.LOGGER.warn("Could not save IX Skins config to {}", SAVE_FILE, exception);
        }
    }

    public static void syncAll(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            sendTo(player);
        }
    }

    public static boolean sendTo(ServerPlayerEntity player) {
        try {
            if (!ServerPlayNetworking.canSend(player, IxSkinsSyncPayload.ID.id())) {
                return false;
            }

            ServerPlayNetworking.send(player, new IxSkinsSyncPayload(snapshot()));
            return true;
        } catch (Throwable throwable) {
            IxSkins.LOGGER.debug("Could not sync IX Skins to {}", player.getName().getString(), throwable);
            return false;
        }
    }
}
