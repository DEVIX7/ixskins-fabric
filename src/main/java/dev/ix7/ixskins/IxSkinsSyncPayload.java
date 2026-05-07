package dev.devix7.ixskins;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record IxSkinsSyncPayload(Map<UUID, SkinSource> snapshot) implements CustomPayload {
    public static final CustomPayload.Id<IxSkinsSyncPayload> ID = new CustomPayload.Id<>(Identifier.of(IxSkins.MOD_ID, "sync"));
    public static final PacketCodec<RegistryByteBuf, IxSkinsSyncPayload> CODEC = PacketCodec.of(
        IxSkinsSyncPayload::write,
        IxSkinsSyncPayload::new
    );

    private static final int MAX_STRING_LENGTH = 32767;

    public IxSkinsSyncPayload {
        snapshot = Map.copyOf(snapshot);
    }

    private IxSkinsSyncPayload(RegistryByteBuf buf) {
        this(readSnapshot(buf));
    }

    private void write(RegistryByteBuf buf) {
        writeSnapshot(buf, snapshot);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }

    public static void registerPayload() {
        PayloadTypeRegistry.playS2C().register(ID, CODEC);
    }

    private static void writeSnapshot(PacketByteBuf buf, Map<UUID, SkinSource> snapshot) {
        buf.writeVarInt(snapshot.size());

        for (Map.Entry<UUID, SkinSource> entry : snapshot.entrySet()) {
            SkinSource source = entry.getValue();
            buf.writeUuid(entry.getKey());
            buf.writeEnumConstant(source.kind());
            buf.writeString(source.value(), MAX_STRING_LENGTH);
        }
    }

    private static Map<UUID, SkinSource> readSnapshot(PacketByteBuf buf) {
        int count = buf.readVarInt();
        Map<UUID, SkinSource> snapshot = new LinkedHashMap<>(count);

        for (int i = 0; i < count; i++) {
            UUID uuid = buf.readUuid();
            SkinSource.Kind kind = buf.readEnumConstant(SkinSource.Kind.class);
            String value = buf.readString(MAX_STRING_LENGTH);
            snapshot.put(uuid, new SkinSource(kind, value));
        }

        return snapshot;
    }
}
