package com.dragoncare.network;

import com.dragoncare.DragonCare;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.UUID;

/** Server → client packet carrying current dirt level for a single dragon. */
public record DragonDirtSyncPayload(UUID dragonId, int dirtLevel) implements CustomPayload {

    public static final CustomPayload.Id<DragonDirtSyncPayload> ID =
            new CustomPayload.Id<>(Identifier.of(DragonCare.MOD_ID, "dirt_sync"));

    public static final PacketCodec<ByteBuf, DragonDirtSyncPayload> CODEC = PacketCodec.tuple(
            Uuids.PACKET_CODEC, DragonDirtSyncPayload::dragonId,
            PacketCodecs.VAR_INT, DragonDirtSyncPayload::dirtLevel,
            DragonDirtSyncPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
