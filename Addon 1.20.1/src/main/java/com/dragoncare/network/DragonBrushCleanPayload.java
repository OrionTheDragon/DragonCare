package com.dragoncare.network;

import com.dragoncare.item.ModItems;
import com.iafenvoy.iceandfire.entity.EntityDragonBase;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record DragonBrushCleanPayload(UUID dragonId, int newDirtLevel, int extraDamage) {

    public DragonBrushCleanPayload(PacketByteBuf buf) {
        this(buf.readUuid(), buf.readVarInt(), buf.readVarInt());
    }

    public void encode(PacketByteBuf buf) {
        buf.writeUuid(this.dragonId);
        buf.writeVarInt(this.newDirtLevel);
        buf.writeVarInt(this.extraDamage);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayerEntity sp = ctx.getSender();
            if (sp == null) return;
            if (!(sp.getWorld() instanceof ServerWorld sw)) return;
            net.minecraft.entity.Entity entity = sw.getEntity(this.dragonId);
            if (!(entity instanceof EntityDragonBase dragon)) return;

            Hand brushHand = Hand.MAIN_HAND;
            ItemStack brush = sp.getStackInHand(Hand.MAIN_HAND);
            if (!brush.isOf(ModItems.DRAGON_BRUSH.get())) {
                brush = sp.getStackInHand(Hand.OFF_HAND);
                brushHand = Hand.OFF_HAND;
            }

            if (brush.isOf(ModItems.DRAGON_BRUSH.get())) {
                int oldDirtLevel = com.dragoncare.mechanics.DragonDirtManager.getDirtLevel(sp.getServer(), dragon.getUuid());
                // Чистка может только уменьшать грязь: зажимаем клиентское значение в [0, oldDirtLevel],
                // иначе поддельный пакет с отрицательным newDirtLevel даёт неограниченные очки связи.
                int newDirtLevel = Math.max(0, Math.min(this.newDirtLevel, oldDirtLevel));
                com.dragoncare.mechanics.DragonDirtManager.setDirtLevel(dragon, newDirtLevel);
                if (this.extraDamage > 0 && !sp.isCreative()) {
                    final Hand finalBrushHand = brushHand;
                    brush.damage(this.extraDamage, sp, (p) -> p.sendToolBreakStatus(finalBrushHand));
                }

                int cleanedLevels = oldDirtLevel - newDirtLevel;
                if (cleanedLevels > 0) {
                    boolean isPerfect = (newDirtLevel == 0 && oldDirtLevel >= 3);
                    int multiplier = isPerfect ? 2 : 1;
                    int points = cleanedLevels * multiplier;
                    com.dragoncare.taming.BondManager.onFeedBypass(sp, dragon, points);

                    if (newDirtLevel == 0) {
                        if (oldDirtLevel == 1) {
                            com.dragoncare.advancement.AchievementGranter.grant(sp, com.dragoncare.advancement.AchievementGranter.FRESH_LOOK);
                        } else if (oldDirtLevel == 5) {
                            com.dragoncare.advancement.AchievementGranter.grant(sp, com.dragoncare.advancement.AchievementGranter.HERCULES_FEAT);
                        }
                    }
                }
            }
        });
        ctx.setPacketHandled(true);
    }
}



