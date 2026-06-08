package com.dragoncare.dragonphone.net;

import com.dragoncare.DragonCare;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * S2C: периодическое обновление состояния HUD-оверлея «Драконьего телефона».
 *
 * <p>Если {@link #active()} {@code false} — клиент должен скрыть оверлей (телефон выключен,
 * выбор сброшен или дракона больше нет). Остальные поля в таком случае пустые/нулевые.</p>
 *
 * <p>При {@code active == true} клиент рисует имя, координаты и расстояние до выбранного
 * дракона. Позиция — серверная (живая, если дракон загружен, иначе последняя известная
 * из реестра), что позволяет показывать данные даже для драконов из выгруженных чанков
 * и из других измерений.</p>
 */
public record PhoneHudPayload(
        boolean active,
        UUID dragonId,
        Identifier dimension,
        double x,
        double y,
        double z,
        String customName,
        boolean hasCustomName,
        String species
) implements CustomPayload {

    public static final CustomPayload.Id<PhoneHudPayload> ID =
            new CustomPayload.Id<>(Identifier.of(DragonCare.MOD_ID, "phone_hud"));

    /** Пустой «clear»-пакет для скрытия оверлея. */
    public static PhoneHudPayload off() {
        return new PhoneHudPayload(false, new UUID(0L, 0L),
                Identifier.of("minecraft", "overworld"),
                0, 0, 0, "", false, "");
    }

    public static final PacketCodec<ByteBuf, PhoneHudPayload> CODEC = new PacketCodec<>() {
        @Override
        public PhoneHudPayload decode(ByteBuf buf) {
            PacketByteBuf pbb = new PacketByteBuf(buf);
            boolean active = pbb.readBoolean();
            UUID id = pbb.readUuid();
            Identifier dim = pbb.readIdentifier();
            double x = pbb.readDouble();
            double y = pbb.readDouble();
            double z = pbb.readDouble();
            String name = pbb.readString(64);
            boolean hasName = pbb.readBoolean();
            String species = pbb.readString(128);
            return new PhoneHudPayload(active, id, dim, x, y, z, name, hasName, species);
        }

        @Override
        public void encode(ByteBuf buf, PhoneHudPayload p) {
            PacketByteBuf pbb = new PacketByteBuf(buf);
            pbb.writeBoolean(p.active);
            pbb.writeUuid(p.dragonId);
            pbb.writeIdentifier(p.dimension);
            pbb.writeDouble(p.x);
            pbb.writeDouble(p.y);
            pbb.writeDouble(p.z);
            pbb.writeString(p.customName == null ? "" : p.customName, 64);
            pbb.writeBoolean(p.hasCustomName);
            pbb.writeString(p.species == null ? "" : p.species, 128);
        }
    };

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
