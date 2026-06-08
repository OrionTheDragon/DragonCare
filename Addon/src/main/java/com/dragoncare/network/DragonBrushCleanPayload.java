package com.dragoncare.network;

import com.dragoncare.DragonCare;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.UUID;

/**
 * Client → Server packet requesting to update the dragon's dirtiness stage after a QTE,
 * and applying extra brush damage on failures.
 */
public record DragonBrushCleanPayload(UUID dragonId, int newDirtLevel, int extraDamage) implements CustomPayload {

    public static final CustomPayload.Id<DragonBrushCleanPayload> ID =
            new CustomPayload.Id<>(Identifier.of(DragonCare.MOD_ID, "brush_clean"));

    public static final PacketCodec<ByteBuf, DragonBrushCleanPayload> CODEC = PacketCodec.tuple(
            Uuids.PACKET_CODEC, DragonBrushCleanPayload::dragonId,
            PacketCodecs.VAR_INT, DragonBrushCleanPayload::newDirtLevel,
            PacketCodecs.VAR_INT, DragonBrushCleanPayload::extraDamage,
            DragonBrushCleanPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
