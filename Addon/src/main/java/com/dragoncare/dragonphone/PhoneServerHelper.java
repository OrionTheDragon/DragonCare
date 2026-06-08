package com.dragoncare.dragonphone;

import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import com.dragoncare.dragonphone.net.DragonInfo;
import com.dragoncare.dragonphone.net.DragonListPayload;
import com.dragoncare.item.ModItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Серверные утилиты «Драконьего телефона»: сборка списка, поиск дракона, отправка пакетов. */
public final class PhoneServerHelper {

    /** Бит ENTITY_FLAGS, отвечающий за glow-эффект. */
    public static final byte FLAG_GLOWING = 0x40;

    /** Дистанция, при достижении которой телефон автоматически выключается. */
    public static final double AUTO_OFF_DISTANCE = 10.0;

    private PhoneServerHelper() {}

    // ------------------------ Список драконов ------------------------

    /**
     * Собирает список прирученных драконов игрока: live-сущности из загруженных миров
     * + записи реестра для выгруженных. Live-данные имеют приоритет.
     */
    public static List<DragonInfo> buildList(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return Collections.emptyList();

        UUID owner = player.getUuid();
        DragonRegistryState reg = DragonRegistryState.get(server);

        // Сначала берём актуальные live-сущности — если дракон загружен, его позиция точная.
        java.util.Map<UUID, DragonInfo> byId = new java.util.LinkedHashMap<>();
        for (ServerWorld world : server.getWorlds()) {
            for (Entity e : world.iterateEntities()) {
                if (!(e instanceof DragonBaseEntity dragon)) continue;
                if (!owner.equals(dragon.getOwnerUuid())) continue;
                byId.put(dragon.getUuid(), liveInfo(dragon));
                // Заодно обновим реестр свежими данными.
                reg.putOrUpdate(dragon, world.getTime());
            }
        }

        // Дополняем тем, что есть в реестре, но сейчас не загружено.
        for (DragonRecord r : reg.ownedBy(owner)) {
            byId.putIfAbsent(r.dragonId(), recordInfo(r));
        }

        return new ArrayList<>(byId.values());
    }

    private static DragonInfo liveInfo(DragonBaseEntity d) {
        Vec3d p = d.getPos();
        Identifier dim = d.getWorld().getRegistryKey().getValue();
        Text custom = d.getCustomName();
        boolean has = custom != null;
        return new DragonInfo(d.getUuid(), dim, p.x, p.y, p.z,
                has ? custom.getString() : "",
                has, d.getDragonStage(),
                d.getType().getTranslationKey());
    }

    private static DragonInfo recordInfo(DragonRecord r) {
        boolean has = r.customName() != null && !r.customName().isEmpty();
        String species = r.species();
        return new DragonInfo(r.dragonId(), r.dimension(), r.x(), r.y(), r.z(),
                has ? r.customName() : "", has, r.stage(),
                species != null ? species : "");
    }

    /** Найти живую сущность дракона по UUID во всех ServerWorld-ах. */
    @Nullable
    public static DragonBaseEntity findLive(MinecraftServer server, UUID dragonId) {
        if (dragonId == null) return null;
        for (ServerWorld world : server.getWorlds()) {
            Entity e = world.getEntity(dragonId);
            if (e instanceof DragonBaseEntity dragon) return dragon;
        }
        return null;
    }

    // ------------------------ Отправка списка ------------------------

    public static void sendList(ServerPlayerEntity player, boolean openScreen, @Nullable UUID selected) {
        List<DragonInfo> list = buildList(player);
        PacketDistributor.sendToPlayer(player,
                new DragonListPayload(list, selected, openScreen));
    }

    // ------------------------ Поиск активного телефона в инвентаре ------------------------

    /** Возвращает первый ItemStack-телефон со «вкл» в инвентаре игрока, либо {@code null}. */
    @Nullable
    public static ItemStack findActivePhone(ServerPlayerEntity player) {
        var inv = player.getInventory();
        // PlayerInventory в yarn 1.21.1 не имеет публичного getStack(int) — обходим main + offHand + armor.
        ItemStack found = scanList(inv.main);
        if (found != null) return found;
        found = scanList(inv.offHand);
        if (found != null) return found;
        return scanList(inv.armor);
    }

    @Nullable
    private static ItemStack scanList(java.util.List<ItemStack> list) {
        for (ItemStack s : list) {
            if (s.isEmpty()) continue;
            if (s.getItem() != ModItems.DRAGON_PHONE.get()) continue;
            Boolean on = s.get(ModDataComponents.PHONE_ON.get());
            if (on != null && on) return s;
        }
        return null;
    }

    // ------------------------ Per-player glow ------------------------

    /**
     * Отправляет владельцу телефона спуфленный {@link EntityTrackerUpdateS2CPacket}
     * с битом GLOWING в ENTITY_FLAGS — остальные клиенты этот пакет не получают,
     * поэтому свечение видит только держатель телефона.
     *
     * <p>Базовый байт флагов реконструируется из публичных геттеров сущности —
     * это аккуратнее, чем отражение приватного {@code Entity.FLAGS}.</p>
     */
    public static void sendPersonalGlow(ServerPlayerEntity player, DragonBaseEntity dragon) {
        byte flags = (byte)(reconstructEntityFlags(dragon) | FLAG_GLOWING);
        DataTracker.SerializedEntry<Byte> entry = new DataTracker.SerializedEntry<>(
                0, TrackedDataHandlerRegistry.BYTE, flags);
        var list = java.util.List.<DataTracker.SerializedEntry<?>>of(entry);
        EntityTrackerUpdateS2CPacket pkt = new EntityTrackerUpdateS2CPacket(dragon.getId(), list);
        player.networkHandler.send(pkt, null);
    }

    /**
     * Отправляет владельцу пакет, сбрасывающий бит GLOWING в актуальное состояние сущности
     * (т.е. убираем персональное свечение). Должно вызываться, когда телефон выключается
     * или меняется выбранный дракон.
     */
    public static void clearPersonalGlow(ServerPlayerEntity player, DragonBaseEntity dragon) {
        byte flags = reconstructEntityFlags(dragon);
        DataTracker.SerializedEntry<Byte> entry = new DataTracker.SerializedEntry<>(
                0, TrackedDataHandlerRegistry.BYTE, flags);
        var list = java.util.List.<DataTracker.SerializedEntry<?>>of(entry);
        EntityTrackerUpdateS2CPacket pkt = new EntityTrackerUpdateS2CPacket(dragon.getId(), list);
        player.networkHandler.send(pkt, null);
    }

    /**
     * Реконструирует байт ENTITY_FLAGS на основе публичных геттеров — это компилируется
     * без рефлексии и без mixin/access-widener.
     * Биты см. {@code Entity}: 0=onFire, 1=sneaking, 3=sprinting, 4=swimming,
     * 5=invisible, 6=glowing, 7=fall flying.
     */
    public static byte reconstructEntityFlags(Entity e) {
        byte b = 0;
        if (e.isOnFire() && !e.isFireImmune()) b |= 0x01;
        if (e.isSneaking()) b |= 0x02;
        if (e.isSprinting()) b |= 0x08;
        if (e.isSwimming()) b |= 0x10;
        if (e.isInvisible()) b |= 0x20;
        if (e.isGlowing()) b |= 0x40;
        // FALL_FLYING (бит 7) живёт на LivingEntity — для драконов фактически не используется
        // (у них своя система полёта), поэтому пропускаем без потери визуальной информации.
        if (e instanceof net.minecraft.entity.LivingEntity le && le.isFallFlying()) b |= 0x80;
        return b;
    }
}
