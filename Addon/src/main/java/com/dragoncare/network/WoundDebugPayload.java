package com.dragoncare.network;

import com.dragoncare.DragonCare;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.UUID;

public record WoundDebugPayload(UUID dragonId, int mode, int percent) implements CustomPayload {
    public static final int MODE_RESET = 0;
    public static final int MODE_FIXED = 1;
    public static final int MODE_ANIMATE = 2;

    public static final CustomPayload.Id<WoundDebugPayload> ID =
            new CustomPayload.Id<>(Identifier.of(DragonCare.MOD_ID, "wound_debug"));

    public static final PacketCodec<ByteBuf, WoundDebugPayload> CODEC = PacketCodec.tuple(
            Uuids.PACKET_CODEC, WoundDebugPayload::dragonId,
            PacketCodecs.VAR_INT, WoundDebugPayload::mode,
            PacketCodecs.VAR_INT, WoundDebugPayload::percent,
            WoundDebugPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
