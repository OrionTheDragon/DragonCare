package com.dragoncare.network;

import com.dragoncare.DragonCare;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.UUID;

/** Server → owner-client packet carrying current bond points for a single dragon. */
public record BondSyncPayload(UUID dragonId, int points) implements CustomPayload {

    public static final CustomPayload.Id<BondSyncPayload> ID =
            new CustomPayload.Id<>(Identifier.of(DragonCare.MOD_ID, "bond_sync"));

    public static final PacketCodec<ByteBuf, BondSyncPayload> CODEC = PacketCodec.tuple(
            Uuids.PACKET_CODEC, BondSyncPayload::dragonId,
            PacketCodecs.VAR_INT,  BondSyncPayload::points,
            BondSyncPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
