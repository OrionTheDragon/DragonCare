package com.dragoncare.dragonphone.net;

import com.dragoncare.DragonCare;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S: клиент просит сервер прислать актуальный список драконов. */
public record RefreshListPayload() implements CustomPayload {

    public static final CustomPayload.Id<RefreshListPayload> ID =
            new CustomPayload.Id<>(Identifier.of(DragonCare.MOD_ID, "phone_refresh"));

    public static final PacketCodec<ByteBuf, RefreshListPayload> CODEC = PacketCodec.unit(new RefreshListPayload());

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
