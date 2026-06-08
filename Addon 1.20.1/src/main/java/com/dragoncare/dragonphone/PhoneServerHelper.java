package com.dragoncare.dragonphone;

import com.iafenvoy.iceandfire.entity.EntityDragonBase;
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

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** РЎРµСЂРІРµСЂРЅС‹Рµ СѓС‚РёР»РёС‚С‹ В«Р”СЂР°РєРѕРЅСЊРµРіРѕ С‚РµР»РµС„РѕРЅР°В»: СЃР±РѕСЂРєР° СЃРїРёСЃРєР°, РїРѕРёСЃРє РґСЂР°РєРѕРЅР°, РѕС‚РїСЂР°РІРєР° РїР°РєРµС‚РѕРІ. */
public final class PhoneServerHelper {

    /** Р‘РёС‚ ENTITY_FLAGS, РѕС‚РІРµС‡Р°СЋС‰РёР№ Р·Р° glow-СЌС„С„РµРєС‚. */
    public static final byte FLAG_GLOWING = 0x40;

    /** Р”РёСЃС‚Р°РЅС†РёСЏ, РїСЂРё РґРѕСЃС‚РёР¶РµРЅРёРё РєРѕС‚РѕСЂРѕР№ С‚РµР»РµС„РѕРЅ Р°РІС‚РѕРјР°С‚РёС‡РµСЃРєРё РІС‹РєР»СЋС‡Р°РµС‚СЃСЏ. */
    public static final double AUTO_OFF_DISTANCE = 10.0;

    private PhoneServerHelper() {}

    // ------------------------ РЎРїРёСЃРѕРє РґСЂР°РєРѕРЅРѕРІ ------------------------

    /**
     * РЎРѕР±РёСЂР°РµС‚ СЃРїРёСЃРѕРє РїСЂРёСЂСѓС‡РµРЅРЅС‹С… РґСЂР°РєРѕРЅРѕРІ РёРіСЂРѕРєР°: live-СЃСѓС‰РЅРѕСЃС‚Рё РёР· Р·Р°РіСЂСѓР¶РµРЅРЅС‹С… РјРёСЂРѕРІ
     * + Р·Р°РїРёСЃРё СЂРµРµСЃС‚СЂР° РґР»СЏ РІС‹РіСЂСѓР¶РµРЅРЅС‹С…. Live-РґР°РЅРЅС‹Рµ РёРјРµСЋС‚ РїСЂРёРѕСЂРёС‚РµС‚.
     */
    public static List<DragonInfo> buildList(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return Collections.emptyList();

        UUID owner = player.getUuid();
        DragonRegistryState reg = DragonRegistryState.get(server);

        // РЎРЅР°С‡Р°Р»Р° Р±РµСЂС‘Рј Р°РєС‚СѓР°Р»СЊРЅС‹Рµ live-СЃСѓС‰РЅРѕСЃС‚Рё вЂ” РµСЃР»Рё РґСЂР°РєРѕРЅ Р·Р°РіСЂСѓР¶РµРЅ, РµРіРѕ РїРѕР·РёС†РёСЏ С‚РѕС‡РЅР°СЏ.
        java.util.Map<UUID, DragonInfo> byId = new java.util.LinkedHashMap<>();
        for (ServerWorld world : server.getWorlds()) {
            for (Entity e : world.iterateEntities()) {
                if (!(e instanceof EntityDragonBase dragon)) continue;
                if (!owner.equals(dragon.getOwnerUuid())) continue;
                byId.put(dragon.getUuid(), liveInfo(dragon));
                // Р—Р°РѕРґРЅРѕ РѕР±РЅРѕРІРёРј СЂРµРµСЃС‚СЂ СЃРІРµР¶РёРјРё РґР°РЅРЅС‹РјРё.
                reg.putOrUpdate(dragon, world.getTime());
            }
        }

        // Р”РѕРїРѕР»РЅСЏРµРј С‚РµРј, С‡С‚Рѕ РµСЃС‚СЊ РІ СЂРµРµСЃС‚СЂРµ, РЅРѕ СЃРµР№С‡Р°СЃ РЅРµ Р·Р°РіСЂСѓР¶РµРЅРѕ.
        for (DragonRecord r : reg.ownedBy(owner)) {
            byId.putIfAbsent(r.dragonId(), recordInfo(r));
        }

        return new ArrayList<>(byId.values());
    }

    private static DragonInfo liveInfo(EntityDragonBase d) {
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

    /** РќР°Р№С‚Рё Р¶РёРІСѓСЋ СЃСѓС‰РЅРѕСЃС‚СЊ РґСЂР°РєРѕРЅР° РїРѕ UUID РІРѕ РІСЃРµС… ServerWorld-Р°С…. */
    @Nullable
    public static EntityDragonBase findLive(MinecraftServer server, UUID dragonId) {
        if (dragonId == null) return null;
        for (ServerWorld world : server.getWorlds()) {
            Entity e = world.getEntity(dragonId);
            if (e instanceof EntityDragonBase dragon) return dragon;
        }
        return null;
    }

    // ------------------------ РћС‚РїСЂР°РІРєР° СЃРїРёСЃРєР° ------------------------

    public static void sendList(ServerPlayerEntity player, boolean openScreen, @Nullable UUID selected) {
        List<DragonInfo> list = buildList(player);
        com.dragoncare.network.ModNetwork.INSTANCE.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> (net.minecraft.server.network.ServerPlayerEntity)player), new DragonListPayload(list, selected, openScreen));
    }

    // ------------------------ РџРѕРёСЃРє Р°РєС‚РёРІРЅРѕРіРѕ С‚РµР»РµС„РѕРЅР° РІ РёРЅРІРµРЅС‚Р°СЂРµ ------------------------

    /** Р’РѕР·РІСЂР°С‰Р°РµС‚ РїРµСЂРІС‹Р№ ItemStack-С‚РµР»РµС„РѕРЅ СЃРѕ В«РІРєР»В» РІ РёРЅРІРµРЅС‚Р°СЂРµ РёРіСЂРѕРєР°, Р»РёР±Рѕ {@code null}. */
    @Nullable
    public static ItemStack findActivePhone(ServerPlayerEntity player) {
        var inv = player.getInventory();
        // PlayerInventory РІ yarn 1.21.1 РЅРµ РёРјРµРµС‚ РїСѓР±Р»РёС‡РЅРѕРіРѕ getStack(int) вЂ” РѕР±С…РѕРґРёРј main + offHand + armor.
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
            Boolean on = (s.hasNbt() && s.getNbt().getBoolean("phone_on"));
            if (on != null && on) return s;
        }
        return null;
    }

    // ------------------------ Per-player glow ------------------------

    /**
     * РћС‚РїСЂР°РІР»СЏРµС‚ РІР»Р°РґРµР»СЊС†Сѓ С‚РµР»РµС„РѕРЅР° СЃРїСѓС„Р»РµРЅРЅС‹Р№ {@link EntityTrackerUpdateS2CPacket}
     * СЃ Р±РёС‚РѕРј GLOWING РІ ENTITY_FLAGS вЂ” РѕСЃС‚Р°Р»СЊРЅС‹Рµ РєР»РёРµРЅС‚С‹ СЌС‚РѕС‚ РїР°РєРµС‚ РЅРµ РїРѕР»СѓС‡Р°СЋС‚,
     * РїРѕСЌС‚РѕРјСѓ СЃРІРµС‡РµРЅРёРµ РІРёРґРёС‚ С‚РѕР»СЊРєРѕ РґРµСЂР¶Р°С‚РµР»СЊ С‚РµР»РµС„РѕРЅР°.
     *
     * <p>Р‘Р°Р·РѕРІС‹Р№ Р±Р°Р№С‚ С„Р»Р°РіРѕРІ СЂРµРєРѕРЅСЃС‚СЂСѓРёСЂСѓРµС‚СЃСЏ РёР· РїСѓР±Р»РёС‡РЅС‹С… РіРµС‚С‚РµСЂРѕРІ СЃСѓС‰РЅРѕСЃС‚Рё вЂ”
     * СЌС‚Рѕ Р°РєРєСѓСЂР°С‚РЅРµРµ, С‡РµРј РѕС‚СЂР°Р¶РµРЅРёРµ РїСЂРёРІР°С‚РЅРѕРіРѕ {@code Entity.FLAGS}.</p>
     */
    public static void sendPersonalGlow(ServerPlayerEntity player, EntityDragonBase dragon) {
        byte flags = (byte)(reconstructEntityFlags(dragon) | FLAG_GLOWING);
        DataTracker.SerializedEntry<Byte> entry = new DataTracker.SerializedEntry<>(
                0, TrackedDataHandlerRegistry.BYTE, flags);
        var list = java.util.List.<DataTracker.SerializedEntry<?>>of(entry);
        EntityTrackerUpdateS2CPacket pkt = new EntityTrackerUpdateS2CPacket(dragon.getId(), list);
        player.networkHandler.sendPacket(pkt);
    }

    /**
     * РћС‚РїСЂР°РІР»СЏРµС‚ РІР»Р°РґРµР»СЊС†Сѓ РїР°РєРµС‚, СЃР±СЂР°СЃС‹РІР°СЋС‰РёР№ Р±РёС‚ GLOWING РІ Р°РєС‚СѓР°Р»СЊРЅРѕРµ СЃРѕСЃС‚РѕСЏРЅРёРµ СЃСѓС‰РЅРѕСЃС‚Рё
     * (С‚.Рµ. СѓР±РёСЂР°РµРј РїРµСЂСЃРѕРЅР°Р»СЊРЅРѕРµ СЃРІРµС‡РµРЅРёРµ). Р”РѕР»Р¶РЅРѕ РІС‹Р·С‹РІР°С‚СЊСЃСЏ, РєРѕРіРґР° С‚РµР»РµС„РѕРЅ РІС‹РєР»СЋС‡Р°РµС‚СЃСЏ
     * РёР»Рё РјРµРЅСЏРµС‚СЃСЏ РІС‹Р±СЂР°РЅРЅС‹Р№ РґСЂР°РєРѕРЅ.
     */
    public static void clearPersonalGlow(ServerPlayerEntity player, EntityDragonBase dragon) {
        byte flags = reconstructEntityFlags(dragon);
        DataTracker.SerializedEntry<Byte> entry = new DataTracker.SerializedEntry<>(
                0, TrackedDataHandlerRegistry.BYTE, flags);
        var list = java.util.List.<DataTracker.SerializedEntry<?>>of(entry);
        EntityTrackerUpdateS2CPacket pkt = new EntityTrackerUpdateS2CPacket(dragon.getId(), list);
        player.networkHandler.sendPacket(pkt);
    }

    /**
     * Р РµРєРѕРЅСЃС‚СЂСѓРёСЂСѓРµС‚ Р±Р°Р№С‚ ENTITY_FLAGS РЅР° РѕСЃРЅРѕРІРµ РїСѓР±Р»РёС‡РЅС‹С… РіРµС‚С‚РµСЂРѕРІ вЂ” СЌС‚Рѕ РєРѕРјРїРёР»РёСЂСѓРµС‚СЃСЏ
     * Р±РµР· СЂРµС„Р»РµРєСЃРёРё Рё Р±РµР· mixin/access-widener.
     * Р‘РёС‚С‹ СЃРј. {@code Entity}: 0=onFire, 1=sneaking, 3=sprinting, 4=swimming,
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
        // FALL_FLYING (Р±РёС‚ 7) Р¶РёРІС‘С‚ РЅР° LivingEntity вЂ” РґР»СЏ РґСЂР°РєРѕРЅРѕРІ С„Р°РєС‚РёС‡РµСЃРєРё РЅРµ РёСЃРїРѕР»СЊР·СѓРµС‚СЃСЏ
        // (Сѓ РЅРёС… СЃРІРѕСЏ СЃРёСЃС‚РµРјР° РїРѕР»С‘С‚Р°), РїРѕСЌС‚РѕРјСѓ РїСЂРѕРїСѓСЃРєР°РµРј Р±РµР· РїРѕС‚РµСЂРё РІРёР·СѓР°Р»СЊРЅРѕР№ РёРЅС„РѕСЂРјР°С†РёРё.
        if (e instanceof net.minecraft.entity.LivingEntity le && le.isFallFlying()) b |= 0x80;
        return b;
    }
}



