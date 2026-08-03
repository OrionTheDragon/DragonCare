package com.dragoncare.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record WoundDebugPayload(UUID dragonId, int mode, int percent) {
    public static final int MODE_RESET = 0;
    public static final int MODE_FIXED = 1;
    public static final int MODE_ANIMATE = 2;

    public WoundDebugPayload(PacketByteBuf buf) {
        this(buf.readUuid(), buf.readVarInt(), buf.readVarInt());
    }

    public void encode(PacketByteBuf buf) {
        buf.writeUuid(this.dragonId);
        buf.writeVarInt(this.mode);
        buf.writeVarInt(this.percent);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() ->
                com.dragoncare.client.ClientWoundDebugState.apply(this.dragonId, this.mode, this.percent)
        );
        context.setPacketHandled(true);
    }
}
