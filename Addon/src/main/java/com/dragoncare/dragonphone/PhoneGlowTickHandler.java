package com.dragoncare.dragonphone;

import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import com.dragoncare.DragonCare;
import com.dragoncare.dragonphone.net.PhoneHudPayload;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Централизованная логика personal-glow для «Драконьего телефона».
 *
 * <p>Каждый тик для каждого онлайн-игрока определяется единственный «активный»
 * телефон (первый со включённым флагом в инвентаре) и его {@code PHONE_TRACKED}.
 * Эта схема гарантирует, что:</p>
 * <ul>
 *   <li>светится максимум ОДИН дракон у игрока — даже если в инвентаре несколько
 *       включённых телефонов, обрабатывается только первый из них;</li>
 *   <li>при смене отслеживаемого дракона предыдущий гарантированно гасится
 *       (без рассинхронизации, как было при ручных вызовах из разных мест);</li>
 *   <li>при перекладывании телефона в сундук, выкидывании на землю или просто
 *       выключении подсветка снимается автоматически на следующем тике.</li>
 * </ul>
 *
 * <p>Хранилище {@link #LAST_GLOWED} живёт в памяти процесса — это не «настоящее»
 * состояние мира, а кеш «какой пакет последним отправили клиенту», нужный
 * исключительно чтобы вовремя послать «погасить».</p>
 */
@EventBusSubscriber(modid = DragonCare.MOD_ID)
public final class PhoneGlowTickHandler {

    /** Период между «подкидываниями» glow-пакета. Без этого vanilla-tracker через несколько тиков перепишет flags. */
    private static final int GLOW_INTERVAL_TICKS = 10;

    /** Период обновления HUD-оверлея (координаты + дистанция дракона). */
    private static final int HUD_INTERVAL_TICKS = 5;

    /** playerUUID -> dragonUUID, на которого последний раз отсылали glow. */
    private static final Map<UUID, UUID> LAST_GLOWED = new HashMap<>();

    /** playerUUID -> был ли последний раз отправлен «active» HUD-пакет. Нужно, чтобы один раз отправить «off». */
    private static final Map<UUID, Boolean> LAST_HUD_ACTIVE = new HashMap<>();

    private PhoneGlowTickHandler() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayerEntity sp)) return;
        
        // Оптимизация: не нужно сканировать инвентарь каждый тик для каждого игрока
        // Проверяем раз в 5 тиков (совпадает с HUD_INTERVAL_TICKS)
        if (sp.getWorld().getTime() % 5 != 0) return;
        
        MinecraftServer server = sp.getServer();
        if (server == null) return;

        UUID playerId = sp.getUuid();
        ItemStack phone = PhoneServerHelper.findActivePhone(sp);
        UUID tracked = phone != null ? phone.get(ModDataComponents.PHONE_TRACKED.get()) : null;
        UUID prev = LAST_GLOWED.get(playerId);

        // Если выбранный дракон сменился (или совсем нет активного телефона) — гасим предыдущего.
        if (prev != null && !prev.equals(tracked)) {
            DragonBaseEntity old = PhoneServerHelper.findLive(server, prev);
            if (old != null) PhoneServerHelper.clearPersonalGlow(sp, old);
            LAST_GLOWED.remove(playerId);
        }

        if (tracked == null) {
            // Телефон не активен или ничего не выбрано — гасим HUD, если он был.
            sendHudOff(sp);
            return;
        }

        DragonBaseEntity dragon = PhoneServerHelper.findLive(server, tracked);
        if (dragon == null) {
            // Дракон в выгруженном чанке — подсветить не можем, но HUD по последним известным
            // координатам из реестра можем (пригодится для драконов в другой размерности/чанке).
            LAST_GLOWED.remove(playerId);
            sendHudFromRecord(sp, tracked);
            return;
        }

        boolean isFresh = !tracked.equals(prev);
        // На первой подсветке отсылаем сразу; далее — раз в N тиков, чтобы vanilla tracker не «съел» наш flag.
        if (isFresh || (sp.getWorld().getTime() % GLOW_INTERVAL_TICKS) == 0) {
            PhoneServerHelper.sendPersonalGlow(sp, dragon);
            LAST_GLOWED.put(playerId, tracked);
        }

        // HUD: позицию берём с живой сущности (свежая). Шлём не каждый тик ради трафика.
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

    private static void sendHudFromLive(ServerPlayerEntity sp, DragonBaseEntity dragon) {
        Vec3d p = dragon.getPos();
        Identifier dim = dragon.getWorld().getRegistryKey().getValue();
        Text custom = dragon.getCustomName();
        boolean has = custom != null;
        PhoneHudPayload payload = new PhoneHudPayload(
                true, dragon.getUuid(), dim, p.x, p.y, p.z,
                has ? custom.getString() : "", has,
                dragon.getType().getTranslationKey());
        PacketDistributor.sendToPlayer(sp, payload);
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
        PacketDistributor.sendToPlayer(sp, payload);
        LAST_HUD_ACTIVE.put(sp.getUuid(), true);
    }

    private static void sendHudOff(ServerPlayerEntity sp) {
        Boolean prev = LAST_HUD_ACTIVE.get(sp.getUuid());
        if (prev != null && prev) {
            PacketDistributor.sendToPlayer(sp, PhoneHudPayload.off());
            LAST_HUD_ACTIVE.put(sp.getUuid(), false);
        }
    }

    public static void clearCache() { LAST_GLOWED.clear(); LAST_HUD_ACTIVE.clear(); }
}
