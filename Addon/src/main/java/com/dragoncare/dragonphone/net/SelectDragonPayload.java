package com.dragoncare.dragonphone.net;

import com.dragoncare.DragonCare;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.UUID;

/**
 * C2S: клиент сообщает серверу выбранного дракона (для записи в компонент стака
 * и активации персонального glow). Если {@link #dragonId()} равен нулевому UUID,
 * выбор сбрасывается.
 */
public record SelectDragonPayload(UUID dragonId) implements CustomPayload {

    public static final CustomPayload.Id<SelectDragonPayload> ID =
            new CustomPayload.Id<>(Identifier.of(DragonCare.MOD_ID, "phone_select"));

    public static final PacketCodec<ByteBuf, SelectDragonPayload> CODEC = PacketCodec.tuple(
            Uuids.PACKET_CODEC, SelectDragonPayload::dragonId,
            SelectDragonPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
