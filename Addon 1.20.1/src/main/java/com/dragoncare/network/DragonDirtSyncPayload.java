package com.dragoncare.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Server → client packet carrying current dirt level for a single dragon. */
public record DragonDirtSyncPayload(UUID dragonId, int dirtLevel) {

    public DragonDirtSyncPayload(PacketByteBuf buf) {
        this(buf.readUuid(), buf.readVarInt());
    }

    public void encode(PacketByteBuf buf) {
        buf.writeUuid(this.dragonId);
        buf.writeVarInt(this.dirtLevel);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            com.dragoncare.client.ClientDirtCache.put(this.dragonId, this.dirtLevel);
        });
        ctx.setPacketHandled(true);
    }
}


