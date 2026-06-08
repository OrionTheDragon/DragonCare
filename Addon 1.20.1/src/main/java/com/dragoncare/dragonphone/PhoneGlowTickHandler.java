package com.dragoncare.dragonphone;

import com.iafenvoy.iceandfire.entity.EntityDragonBase;
import com.dragoncare.DragonCare;
import com.dragoncare.dragonphone.net.PhoneHudPayload;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Р¦РµРЅС‚СЂР°Р»РёР·РѕРІР°РЅРЅР°СЏ Р»РѕРіРёРєР° personal-glow РґР»СЏ В«Р”СЂР°РєРѕРЅСЊРµРіРѕ С‚РµР»РµС„РѕРЅР°В».
 *
 * <p>РљР°Р¶РґС‹Р№ С‚РёРє РґР»СЏ РєР°Р¶РґРѕРіРѕ РѕРЅР»Р°Р№РЅ-РёРіСЂРѕРєР° РѕРїСЂРµРґРµР»СЏРµС‚СЃСЏ РµРґРёРЅСЃС‚РІРµРЅРЅС‹Р№ В«Р°РєС‚РёРІРЅС‹Р№В»
 * С‚РµР»РµС„РѕРЅ (РїРµСЂРІС‹Р№ СЃРѕ РІРєР»СЋС‡С‘РЅРЅС‹Рј С„Р»Р°РіРѕРј РІ РёРЅРІРµРЅС‚Р°СЂРµ) Рё РµРіРѕ {@code PHONE_TRACKED}.
 * Р­С‚Р° СЃС…РµРјР° РіР°СЂР°РЅС‚РёСЂСѓРµС‚, С‡С‚Рѕ:</p>
 * <ul>
 *   <li>СЃРІРµС‚РёС‚СЃСЏ РјР°РєСЃРёРјСѓРј РћР”РРќ РґСЂР°РєРѕРЅ Сѓ РёРіСЂРѕРєР° вЂ” РґР°Р¶Рµ РµСЃР»Рё РІ РёРЅРІРµРЅС‚Р°СЂРµ РЅРµСЃРєРѕР»СЊРєРѕ
 *       РІРєР»СЋС‡С‘РЅРЅС‹С… С‚РµР»РµС„РѕРЅРѕРІ, РѕР±СЂР°Р±Р°С‚С‹РІР°РµС‚СЃСЏ С‚РѕР»СЊРєРѕ РїРµСЂРІС‹Р№ РёР· РЅРёС…;</li>
 *   <li>РїСЂРё СЃРјРµРЅРµ РѕС‚СЃР»РµР¶РёРІР°РµРјРѕРіРѕ РґСЂР°РєРѕРЅР° РїСЂРµРґС‹РґСѓС‰РёР№ РіР°СЂР°РЅС‚РёСЂРѕРІР°РЅРЅРѕ РіР°СЃРёС‚СЃСЏ
 *       (Р±РµР· СЂР°СЃСЃРёРЅС…СЂРѕРЅРёР·Р°С†РёРё, РєР°Рє Р±С‹Р»Рѕ РїСЂРё СЂСѓС‡РЅС‹С… РІС‹Р·РѕРІР°С… РёР· СЂР°Р·РЅС‹С… РјРµСЃС‚);</li>
 *   <li>РїСЂРё РїРµСЂРµРєР»Р°РґС‹РІР°РЅРёРё С‚РµР»РµС„РѕРЅР° РІ СЃСѓРЅРґСѓРє, РІС‹РєРёРґС‹РІР°РЅРёРё РЅР° Р·РµРјР»СЋ РёР»Рё РїСЂРѕСЃС‚Рѕ
 *       РІС‹РєР»СЋС‡РµРЅРёРё РїРѕРґСЃРІРµС‚РєР° СЃРЅРёРјР°РµС‚СЃСЏ Р°РІС‚РѕРјР°С‚РёС‡РµСЃРєРё РЅР° СЃР»РµРґСѓСЋС‰РµРј С‚РёРєРµ.</li>
 * </ul>
 *
 * <p>РҐСЂР°РЅРёР»РёС‰Рµ {@link #LAST_GLOWED} Р¶РёРІС‘С‚ РІ РїР°РјСЏС‚Рё РїСЂРѕС†РµСЃСЃР° вЂ” СЌС‚Рѕ РЅРµ В«РЅР°СЃС‚РѕСЏС‰РµРµВ»
 * СЃРѕСЃС‚РѕСЏРЅРёРµ РјРёСЂР°, Р° РєРµС€ В«РєР°РєРѕР№ РїР°РєРµС‚ РїРѕСЃР»РµРґРЅРёРј РѕС‚РїСЂР°РІРёР»Рё РєР»РёРµРЅС‚СѓВ», РЅСѓР¶РЅС‹Р№
 * РёСЃРєР»СЋС‡РёС‚РµР»СЊРЅРѕ С‡С‚РѕР±С‹ РІРѕРІСЂРµРјСЏ РїРѕСЃР»Р°С‚СЊ В«РїРѕРіР°СЃРёС‚СЊВ».</p>
 */
@EventBusSubscriber(modid = DragonCare.MOD_ID)
public final class PhoneGlowTickHandler {

    /** РџРµСЂРёРѕРґ РјРµР¶РґСѓ В«РїРѕРґРєРёРґС‹РІР°РЅРёСЏРјРёВ» glow-РїР°РєРµС‚Р°. Р‘РµР· СЌС‚РѕРіРѕ vanilla-tracker С‡РµСЂРµР· РЅРµСЃРєРѕР»СЊРєРѕ С‚РёРєРѕРІ РїРµСЂРµРїРёС€РµС‚ flags. */
    private static final int GLOW_INTERVAL_TICKS = 10;

    /** РџРµСЂРёРѕРґ РѕР±РЅРѕРІР»РµРЅРёСЏ HUD-РѕРІРµСЂР»РµСЏ (РєРѕРѕСЂРґРёРЅР°С‚С‹ + РґРёСЃС‚Р°РЅС†РёСЏ РґСЂР°РєРѕРЅР°). */
    private static final int HUD_INTERVAL_TICKS = 5;

    /** playerUUID -> dragonUUID, РЅР° РєРѕС‚РѕСЂРѕРіРѕ РїРѕСЃР»РµРґРЅРёР№ СЂР°Р· РѕС‚СЃС‹Р»Р°Р»Рё glow. */
    private static final Map<UUID, UUID> LAST_GLOWED = new HashMap<>();

    /** playerUUID -> Р±С‹Р» Р»Рё РїРѕСЃР»РµРґРЅРёР№ СЂР°Р· РѕС‚РїСЂР°РІР»РµРЅ В«activeВ» HUD-РїР°РєРµС‚. РќСѓР¶РЅРѕ, С‡С‚РѕР±С‹ РѕРґРёРЅ СЂР°Р· РѕС‚РїСЂР°РІРёС‚СЊ В«offВ». */
    private static final Map<UUID, Boolean> LAST_HUD_ACTIVE = new HashMap<>();

    private PhoneGlowTickHandler() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayerEntity sp)) return;
        
        // РћРїС‚РёРјРёР·Р°С†РёСЏ: РЅРµ РЅСѓР¶РЅРѕ СЃРєР°РЅРёСЂРѕРІР°С‚СЊ РёРЅРІРµРЅС‚Р°СЂСЊ РєР°Р¶РґС‹Р№ С‚РёРє РґР»СЏ РєР°Р¶РґРѕРіРѕ РёРіСЂРѕРєР°
        // РџСЂРѕРІРµСЂСЏРµРј СЂР°Р· РІ 5 С‚РёРєРѕРІ (СЃРѕРІРїР°РґР°РµС‚ СЃ HUD_INTERVAL_TICKS)
        if (sp.getWorld().getTime() % 5 != 0) return;
        
        MinecraftServer server = sp.getServer();
        if (server == null) return;

        UUID playerId = sp.getUuid();
        ItemStack phone = PhoneServerHelper.findActivePhone(sp);
        UUID tracked = phone != null ? (phone.hasNbt() && phone.getNbt().containsUuid("phone_tracked") ? phone.getNbt().getUuid("phone_tracked") : null) : null;
        UUID prev = LAST_GLOWED.get(playerId);

        // Р•СЃР»Рё РІС‹Р±СЂР°РЅРЅС‹Р№ РґСЂР°РєРѕРЅ СЃРјРµРЅРёР»СЃСЏ (РёР»Рё СЃРѕРІСЃРµРј РЅРµС‚ Р°РєС‚РёРІРЅРѕРіРѕ С‚РµР»РµС„РѕРЅР°) вЂ” РіР°СЃРёРј РїСЂРµРґС‹РґСѓС‰РµРіРѕ.
        if (prev != null && !prev.equals(tracked)) {
            EntityDragonBase old = PhoneServerHelper.findLive(server, prev);
            if (old != null) PhoneServerHelper.clearPersonalGlow(sp, old);
            LAST_GLOWED.remove(playerId);
        }

        if (tracked == null) {
            // РўРµР»РµС„РѕРЅ РЅРµ Р°РєС‚РёРІРµРЅ РёР»Рё РЅРёС‡РµРіРѕ РЅРµ РІС‹Р±СЂР°РЅРѕ вЂ” РіР°СЃРёРј HUD, РµСЃР»Рё РѕРЅ Р±С‹Р».
            sendHudOff(sp);
            return;
        }

        EntityDragonBase dragon = PhoneServerHelper.findLive(server, tracked);
        if (dragon == null) {
            // Р”СЂР°РєРѕРЅ РІ РІС‹РіСЂСѓР¶РµРЅРЅРѕРј С‡Р°РЅРєРµ вЂ” РїРѕРґСЃРІРµС‚РёС‚СЊ РЅРµ РјРѕР¶РµРј, РЅРѕ HUD РїРѕ РїРѕСЃР»РµРґРЅРёРј РёР·РІРµСЃС‚РЅС‹Рј
            // РєРѕРѕСЂРґРёРЅР°С‚Р°Рј РёР· СЂРµРµСЃС‚СЂР° РјРѕР¶РµРј (РїСЂРёРіРѕРґРёС‚СЃСЏ РґР»СЏ РґСЂР°РєРѕРЅРѕРІ РІ РґСЂСѓРіРѕР№ СЂР°Р·РјРµСЂРЅРѕСЃС‚Рё/С‡Р°РЅРєРµ).
            LAST_GLOWED.remove(playerId);
            sendHudFromRecord(sp, tracked);
            return;
        }

        boolean isFresh = !tracked.equals(prev);
        // РќР° РїРµСЂРІРѕР№ РїРѕРґСЃРІРµС‚РєРµ РѕС‚СЃС‹Р»Р°РµРј СЃСЂР°Р·Сѓ; РґР°Р»РµРµ вЂ” СЂР°Р· РІ N С‚РёРєРѕРІ, С‡С‚РѕР±С‹ vanilla tracker РЅРµ В«СЃСЉРµР»В» РЅР°С€ flag.
        if (isFresh || (sp.getWorld().getTime() % GLOW_INTERVAL_TICKS) == 0) {
            PhoneServerHelper.sendPersonalGlow(sp, dragon);
            LAST_GLOWED.put(playerId, tracked);
        }

        // HUD: РїРѕР·РёС†РёСЋ Р±РµСЂС‘Рј СЃ Р¶РёРІРѕР№ СЃСѓС‰РЅРѕСЃС‚Рё (СЃРІРµР¶Р°СЏ). РЁР»С‘Рј РЅРµ РєР°Р¶РґС‹Р№ С‚РёРє СЂР°РґРё С‚СЂР°С„РёРєР°.
        if (isFresh || (sp.getWorld().getTime() % HUD_INTERVAL_TICKS) == 0) {
            sendHudFromLive(sp, dragon);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUuid();
        LAST_GLOWED.remove(id);
        LAST_HUD_ACTIVE.remove(id);
    }

    // -------------------- HUD helpers --------------------

    private static void sendHudFromLive(ServerPlayerEntity sp, EntityDragonBase dragon) {
        Vec3d p = dragon.getPos();
        Identifier dim = dragon.getWorld().getRegistryKey().getValue();
        Text custom = dragon.getCustomName();
        boolean has = custom != null;
        PhoneHudPayload payload = new PhoneHudPayload(
                true, dragon.getUuid(), dim, p.x, p.y, p.z,
                has ? custom.getString() : "", has,
                dragon.getType().getTranslationKey());
        com.dragoncare.network.ModNetwork.INSTANCE.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> (net.minecraft.server.network.ServerPlayerEntity)sp), payload);
        LAST_HUD_ACTIVE.put(sp.getUuid(), true);
    }

    private static void sendHudFromRecord(ServerPlayerEntity sp, UUID dragonId) {
        DragonRegistryState state = DragonRegistryState.get(sp.getServer());
        DragonRecord r = null;
        for (DragonRecord rec : state.all()) {
            if (rec.dragonId().equals(dragonId)) { r = rec; break; }
        }
        if (r == null) { sendHudOff(sp); return; }

        boolean has = r.customName() != null && !r.customName().isEmpty();
        String species = r.species();
        PhoneHudPayload payload = new PhoneHudPayload(
                true, r.dragonId(), r.dimension(), r.x(), r.y(), r.z(),
                has ? r.customName() : "", has,
                species != null ? species : "");
        com.dragoncare.network.ModNetwork.INSTANCE.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> (net.minecraft.server.network.ServerPlayerEntity)sp), payload);
        LAST_HUD_ACTIVE.put(sp.getUuid(), true);
    }

    private static void sendHudOff(ServerPlayerEntity sp) {
        Boolean prev = LAST_HUD_ACTIVE.get(sp.getUuid());
        if (prev != null && prev) {
            com.dragoncare.network.ModNetwork.INSTANCE.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> (net.minecraft.server.network.ServerPlayerEntity)sp), PhoneHudPayload.off());
            LAST_HUD_ACTIVE.put(sp.getUuid(), false);
        }
    }

    public static void clearCache() { LAST_GLOWED.clear(); LAST_HUD_ACTIVE.clear(); }
}



