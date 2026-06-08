package com.dragoncare.network;

import com.dragoncare.client.ClientBondCache;
import net.minecraft.network.PacketByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Server → owner-client packet carrying current bond points for a single dragon. */
public record BondSyncPayload(UUID dragonId, int points) {

    public BondSyncPayload(PacketByteBuf buf) {
        this(buf.readUuid(), buf.readVarInt());
    }

    public void encode(PacketByteBuf buf) {
        buf.writeUuid(this.dragonId);
        buf.writeVarInt(this.points);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ClientBondCache.put(this.dragonId, this.points);
        });
        ctx.setPacketHandled(true);
    }
}


