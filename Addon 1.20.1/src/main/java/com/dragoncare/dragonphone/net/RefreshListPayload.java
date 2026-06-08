package com.dragoncare.dragonphone.net;

import com.dragoncare.dragonphone.PhoneServerHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** C2S: клиент просит сервер прислать актуальный список драконов. */
public record RefreshListPayload() {

    public RefreshListPayload(PacketByteBuf buf) {
        this();
    }

    public void encode(PacketByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayerEntity sp = ctx.getSender();
            if (sp != null) {
                ItemStack phone = PhoneServerHelper.findActivePhone(sp);
                UUID selected = null;
                if (phone != null && phone.hasNbt() && phone.getNbt().contains("phone_tracked")) {
                    selected = phone.getNbt().getUuid("phone_tracked");
                }
                PhoneServerHelper.sendList(sp, false, selected);
            }
        });
        ctx.setPacketHandled(true);
    }
}


