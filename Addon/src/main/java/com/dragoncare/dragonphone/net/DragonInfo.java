package com.dragoncare.dragonphone.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Сериализуемая запись об одном драконе для UI телефона: UUID, измерение,
 * координаты, имя (если есть бирка) и стадия роста.
 *
 * <p>Дистанция вычисляется на клиенте сравнением с позицией владельца —
 * передавать её отдельно нет смысла, т.к. она быстро устаревает.</p>
 */
public record DragonInfo(
        UUID dragonId,
        Identifier dimension,
        double x,
        double y,
        double z,
        /** Отображаемое имя — если бирка задавала, то её строка; иначе клиент покажет вид по {@link #species()}. */
        String customName,
        boolean hasCustomName,
        int stage,
        /** Translation key типа сущности, напр. {@code entity.iceandfire.fire_dragon}. Может быть пустым для legacy-записей. */
        String species
) {

    public static final PacketCodec<ByteBuf, DragonInfo> CODEC = new PacketCodec<>() {
        @Override
        public DragonInfo decode(ByteBuf buf) {
            PacketByteBuf pbb = new PacketByteBuf(buf);
            UUID id = pbb.readUuid();
            Identifier dim = pbb.readIdentifier();
            double x = pbb.readDouble();
            double y = pbb.readDouble();
            double z = pbb.readDouble();
            String name = pbb.readString(64);
            boolean has = pbb.readBoolean();
            int stage = pbb.readVarInt();
            String species = pbb.readString(128);
            return new DragonInfo(id, dim, x, y, z, name, has, stage, species);
        }

        @Override
        public void encode(ByteBuf buf, DragonInfo info) {
            PacketByteBuf pbb = new PacketByteBuf(buf);
            pbb.writeUuid(info.dragonId);
            pbb.writeIdentifier(info.dimension);
            pbb.writeDouble(info.x);
            pbb.writeDouble(info.y);
            pbb.writeDouble(info.z);
            pbb.writeString(info.customName == null ? "" : info.customName, 64);
            pbb.writeBoolean(info.hasCustomName);
            pbb.writeVarInt(info.stage);
            pbb.writeString(info.species == null ? "" : info.species, 128);
        }
    };
}
