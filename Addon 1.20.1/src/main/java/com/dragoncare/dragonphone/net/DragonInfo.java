package com.dragoncare.dragonphone.net;

import net.minecraft.network.PacketByteBuf;
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

    public DragonInfo(PacketByteBuf pbb) {
        this(
            pbb.readUuid(),
            pbb.readIdentifier(),
            pbb.readDouble(),
            pbb.readDouble(),
            pbb.readDouble(),
            pbb.readString(64),
            pbb.readBoolean(),
            pbb.readVarInt(),
            pbb.readString(128)
        );
    }

    public void encode(PacketByteBuf pbb) {
        pbb.writeUuid(this.dragonId);
        pbb.writeIdentifier(this.dimension);
        pbb.writeDouble(this.x);
        pbb.writeDouble(this.y);
        pbb.writeDouble(this.z);
        pbb.writeString(this.customName == null ? "" : this.customName, 64);
        pbb.writeBoolean(this.hasCustomName);
        pbb.writeVarInt(this.stage);
        pbb.writeString(this.species == null ? "" : this.species, 128);
    }
}


