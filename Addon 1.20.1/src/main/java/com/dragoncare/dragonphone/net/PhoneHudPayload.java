package com.dragoncare.dragonphone.net;

import com.dragoncare.dragonphone.client.ClientPhoneHudState;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

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
) {

    /** Пустой «clear»-пакет для скрытия оверлея. */
    public static PhoneHudPayload off() {
        return new PhoneHudPayload(false, new UUID(0L, 0L),
                Identifier.of("minecraft", "overworld"),
                0, 0, 0, "", false, "");
    }

    public PhoneHudPayload(PacketByteBuf pbb) {
        this(
            pbb.readBoolean(),
            pbb.readUuid(),
            pbb.readIdentifier(),
            pbb.readDouble(),
            pbb.readDouble(),
            pbb.readDouble(),
            pbb.readString(64),
            pbb.readBoolean(),
            pbb.readString(128)
        );
    }

    public void encode(PacketByteBuf pbb) {
        pbb.writeBoolean(this.active);
        pbb.writeUuid(this.dragonId);
        pbb.writeIdentifier(this.dimension);
        pbb.writeDouble(this.x);
        pbb.writeDouble(this.y);
        pbb.writeDouble(this.z);
        pbb.writeString(this.customName == null ? "" : this.customName, 64);
        pbb.writeBoolean(this.hasCustomName);
        pbb.writeString(this.species == null ? "" : this.species, 128);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ClientPhoneHudState.set(this);
        });
        ctx.setPacketHandled(true);
    }
}


