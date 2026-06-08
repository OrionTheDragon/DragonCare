package com.dragoncare.dragonphone.net;

import com.dragoncare.DragonCare;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C: сервер шлёт владельцу телефона список его прирученных драконов.
 * Также передаёт текущий выбранный UUID (или нулевой UUID), чтобы UI подсветил его.
 * Отправляется при включении телефона и в ответ на запрос обновления.
 */
public record DragonListPayload(List<DragonInfo> dragons, java.util.UUID selected, boolean openScreen)
        implements CustomPayload {

    public static final CustomPayload.Id<DragonListPayload> ID =
            new CustomPayload.Id<>(Identifier.of(DragonCare.MOD_ID, "phone_dragon_list"));

    private static final java.util.UUID ZERO = new java.util.UUID(0L, 0L);

    public static final PacketCodec<ByteBuf, DragonListPayload> CODEC = new PacketCodec<>() {
        @Override
        public DragonListPayload decode(ByteBuf buf) {
            PacketByteBuf pbb = new PacketByteBuf(buf);
            int n = pbb.readVarInt();
            List<DragonInfo> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) list.add(DragonInfo.CODEC.decode(buf));
            java.util.UUID sel = pbb.readUuid();
            boolean open = pbb.readBoolean();
            return new DragonListPayload(list, ZERO.equals(sel) ? null : sel, open);
        }

        @Override
        public void encode(ByteBuf buf, DragonListPayload p) {
            PacketByteBuf pbb = new PacketByteBuf(buf);
            pbb.writeVarInt(p.dragons.size());
            for (DragonInfo info : p.dragons) DragonInfo.CODEC.encode(buf, info);
            pbb.writeUuid(p.selected != null ? p.selected : ZERO);
            pbb.writeBoolean(p.openScreen);
        }
    };

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
