package com.dragoncare.dragonphone.net;

import com.dragoncare.dragonphone.PhoneServerHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * C2S: клиент сообщает серверу выбранного дракона (для записи в компонент стака
 * и активации персонального glow). Если {@link #dragonId()} равен нулевому UUID,
 * выбор сбрасывается.
 */
public record SelectDragonPayload(UUID dragonId) {

    private static final UUID ZERO = new UUID(0L, 0L);

    public SelectDragonPayload(PacketByteBuf buf) {
        this(buf.readUuid());
    }

    public void encode(PacketByteBuf buf) {
        buf.writeUuid(this.dragonId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayerEntity sp = ctx.getSender();
            if (sp != null) {
                ItemStack phone = PhoneServerHelper.findActivePhone(sp);
                if (phone != null) {
                    UUID newSel = ZERO.equals(this.dragonId) ? null : this.dragonId;
                    if (newSel == null) {
                        if (phone.hasNbt()) {
                            phone.getNbt().remove("phone_tracked");
                        }
                    } else {
                        phone.getOrCreateNbt().putUuid("phone_tracked", newSel);
                    }
                }
            }
        });
        ctx.setPacketHandled(true);
    }
}


