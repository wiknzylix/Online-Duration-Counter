package net.wiknzylix.playtimetogether;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.ArrayList;

public record PlaytimePayload(PlaytimeSnapshot snapshot) implements CustomPayload {
    public static final Id<PlaytimePayload> ID = new Id<>(Identifier.of(PlaytimeTogether.MOD_ID, "snapshot_v2"));
    public static final PacketCodec<RegistryByteBuf, PlaytimePayload> CODEC = new PacketCodec<>() {
        public PlaytimePayload decode(RegistryByteBuf b) {
            var worldId = b.readUuid();
            long revision = b.readVarLong();
            boolean paused = b.readBoolean();
            int count = b.readVarInt();
            if (count < 0 || count > 4096) throw new IllegalArgumentException("Invalid ODC snapshot size");
            var rows = new ArrayList<PlaytimeSnapshot.Entry>(count);
            for (int i = 0; i < count; i++) rows.add(new PlaytimeSnapshot.Entry(b.readUuid(), b.readString(64), Math.max(0, b.readVarLong()), b.readBoolean(), b.readBoolean()));
            return new PlaytimePayload(new PlaytimeSnapshot(worldId, revision, paused, rows));
        }
        public void encode(RegistryByteBuf b, PlaytimePayload p) {
            var s = p.snapshot();
            b.writeUuid(s.worldId()); b.writeVarLong(s.revision()); b.writeBoolean(s.paused());
            b.writeVarInt(s.entries().size());
            for (var row : s.entries()) {
                b.writeUuid(row.uuid()); b.writeString(row.name(), 64);
                b.writeVarLong(row.totalMillis()); b.writeBoolean(row.online()); b.writeBoolean(row.fakePlayer());
            }
        }
    };
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
