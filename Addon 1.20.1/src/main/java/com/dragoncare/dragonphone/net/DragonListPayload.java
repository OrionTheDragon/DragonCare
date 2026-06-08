package com.dragoncare.dragonphone.net;

import com.dragoncare.dragonphone.client.ClientPhoneBridge;
import net.minecraft.network.PacketByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * S2C: сервер шлёт владельцу телефона список его прирученных драконов.
 * Также передаёт текущий выбранный UUID (или нулевой UUID), чтобы UI подсветил его.
 * Отправляется при включении телефона и в ответ на запрос обновления.
 */
public record DragonListPayload(List<DragonInfo> dragons, java.util.UUID selected, boolean openScreen) {

    private static final java.util.UUID ZERO = new java.util.UUID(0L, 0L);

    public DragonListPayload(PacketByteBuf pbb) {
        this(readList(pbb), readSelected(pbb), pbb.readBoolean());
    }

    private static List<DragonInfo> readList(PacketByteBuf pbb) {
        int n = pbb.readVarInt();
        List<DragonInfo> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new DragonInfo(pbb));
        }
        return list;
    }

    private static UUID readSelected(PacketByteBuf pbb) {
        UUID sel = pbb.readUuid();
        return ZERO.equals(sel) ? null : sel;
    }

    public void encode(PacketByteBuf pbb) {
        pbb.writeVarInt(this.dragons.size());
        for (DragonInfo info : this.dragons) {
            info.encode(pbb);
        }
        pbb.writeUuid(this.selected != null ? this.selected : ZERO);
        pbb.writeBoolean(this.openScreen);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ClientPhoneBridge.handleList(this);
        });
        ctx.setPacketHandled(true);
    }
}


