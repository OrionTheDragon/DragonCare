package com.dragoncare.taming;

import com.iafenvoy.iceandfire.entity.EntityDragonBase;
import com.iafenvoy.iceandfire.entity.EntityFireDragon;
import com.iafenvoy.iceandfire.entity.EntityIceDragon;
import com.iafenvoy.iceandfire.entity.EntityLightningDragon;
import com.dragoncare.DragonCare;
import com.dragoncare.advancement.AchievementGranter;
import com.dragoncare.network.BondSyncPayload;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Bond/affection facade. All mutations go through this class so we can:
 *   - lazily pay out passive bond on read,
 *   - apply per-window feeding caps,
 *   - keep persistent state and the owner's client view in sync.
 */
public final class BondManager {

    /** Cumulative point thresholds at which the dragon enters levels 0..5. */
    public static final int[] THRESHOLDS = {0, 100, 450, 1050, 1900, 3000};
    public static final int MAX_LEVEL = 5;

    public static final int FEED_BOND = 2;
    /** 36 points per 10 minutes = 18 feedings (each feeding = 2 points). */
    public static final int FEED_CAP_POINTS = 36;
    public static final int FEED_CAP_USES = FEED_CAP_POINTS / FEED_BOND;
    public static final long FEED_WINDOW_TICKS = 20L * 60L * 10L; // 10 minutes
    public static final long PASSIVE_INTERVAL_TICKS = 20L * 60L;  // 1 minute
    public static final int PASSIVE_BOND = 1;
    public static final int EGG_HATCH_STARTING_BOND = THRESHOLDS[1];

    /**
     * Per-effect amplifier per bond level (index 0..MAX_LEVEL). {@code -1} means the effect is
     * not active at this level. Amplifier {@code 0} is roman-numeral "I" in vanilla.
     *
     * <p>Bonus roadmap (the in-game tooltip mirrors this):
     * <ul>
     *   <li>0 вЂ” none</li>
     *   <li>1 вЂ” Resistance I</li>
     *   <li>2 вЂ” Resistance I + Regeneration I</li>
     *   <li>3 вЂ” Resistance II + Regeneration I + Strength I</li>
     *   <li>4 вЂ” Resistance III + Regeneration II + Strength II + 5% max-HP</li>
     *   <li>5 вЂ” Resistance IV + Regeneration III + Strength III + 10% max-HP</li>
     * </ul>
     */
    public static final int[] RESISTANCE_AMP_BY_LEVEL    = {-1,  0,  0,  1,  2,  3};
    public static final int[] REGENERATION_AMP_BY_LEVEL  = {-1, -1,  0,  0,  1,  2};
    public static final int[] STRENGTH_AMP_BY_LEVEL      = {-1, -1, -1,  0,  1,  2};
    /** Bonus max-HP per level as a fraction of base health ({@code ADD_MULTIPLIED_BASE}). */
    public static final double[] HEALTH_PCT_BY_LEVEL     = {0.0, 0.0, 0.0, 0.0, 0.05, 0.10};

    /** Stable identifier for the bond's max-HP attribute modifier. */
    private static final java.util.UUID BOND_HEALTH_MOD_ID = java.util.UUID.fromString("11111111-2222-3333-4444-999999999999");

    private BondManager() {}

    // ---------- queries ----------

    public static int getLevel(int points) {
        for (int lvl = MAX_LEVEL; lvl >= 0; lvl--) {
            if (points >= THRESHOLDS[lvl]) return lvl;
        }
        return 0;
    }

    public static int progressInLevel(int points) {
        int lvl = getLevel(points);
        if (lvl >= MAX_LEVEL) return 0;
        return points - THRESHOLDS[lvl];
    }

    public static int needForNextLevel(int points) {
        int lvl = getLevel(points);
        if (lvl >= MAX_LEVEL) return 0;
        return THRESHOLDS[lvl + 1] - THRESHOLDS[lvl];
    }

    /** Server-side current points (after lazy passive payout). */
    public static int getPoints(MinecraftServer server, UUID dragonId) {
        BondData d = BondState.get(server).peek(dragonId);
        if (d == null) return 0;
        applyPassive(d, server.getOverworld().getTime());
        return d.points;
    }

    // ---------- internals ----------

    /** Folds elapsed passive minutes into {@code d.points} and advances {@code lastPassiveTick}. */
    private static void applyPassive(BondData d, long now) {
        if (d.lastPassiveTick == 0L) {
            d.lastPassiveTick = now;
            return;
        }
        long elapsed = now - d.lastPassiveTick;
        if (elapsed < PASSIVE_INTERVAL_TICKS) return;
        long minutes = elapsed / PASSIVE_INTERVAL_TICKS;
        int gained = (int) Math.min(Integer.MAX_VALUE, minutes) * PASSIVE_BOND;
        d.points = Math.min(THRESHOLDS[MAX_LEVEL], d.points + gained);
        d.lastPassiveTick += minutes * PASSIVE_INTERVAL_TICKS;
    }

    private static String formatTime(long seconds) {
        seconds = Math.max(0L, seconds);
        long m = seconds / 60L;
        long s = seconds % 60L;
        return String.format("%d:%02d", m, s);
    }

    private static void sync(ServerPlayerEntity player, UUID dragonId, BondData d) {
        if (player == null) return;
        com.dragoncare.network.ModNetwork.INSTANCE.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), new BondSyncPayload(dragonId, d.points));
    }

    /** Sends current bond to a player who just started tracking the dragon. */
    public static void syncTo(ServerPlayerEntity player, EntityDragonBase dragon) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        UUID dragonId = dragon.getUuid();
        BondData d = BondState.get(server).peek(dragonId);
        int points = 0;
        if (d != null) {
            applyPassive(d, server.getOverworld().getTime());
            points = d.points;
        }
        com.dragoncare.network.ModNetwork.INSTANCE.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), new BondSyncPayload(dragonId, points));
    }

    // ---------- mutations ----------

    /** Player feeds {@code iceandfire:dragon_meal} to a tamed dragon they own. */
    public static void onFeed(ServerPlayerEntity player, EntityDragonBase dragon) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        if (dragon.getOwnerUuid() == null || !dragon.getOwnerUuid().equals(player.getUuid())) return;

        UUID dragonId = dragon.getUuid();
        BondState state = BondState.get(server);
        long now = server.getOverworld().getTime();

        // Safety net: if this is a freshly hatched baby with no record yet (e.g. the
        // EntityJoinLevelEvent deferred init somehow missed it), seed the record with
        // hatch-starting bond before crediting the feed.
        if (state.peek(dragonId) == null && dragon.getAgeInDays() <= 1) {
            BondData seed = state.getOrCreate(dragonId);
            seed.points = EGG_HATCH_STARTING_BOND;
            seed.lastPassiveTick = now;
            state.markDirty();
        }

        BondData d = state.getOrCreate(dragonId);

        // Dedup: NeoForge may fire PlayerInteractEvent.EntityInteract twice within the same
        // server tick for a single right-click (e.g. when item & entity hooks both run).
        // Crediting the feed only once per tick guarantees +FEED_BOND per actual click.
        if (d.lastFeedTick == now) return;

        applyPassive(d, now);

        // Roll the 10-minute window if it has elapsed
        if (now - d.windowStartTick >= FEED_WINDOW_TICKS) {
            d.windowStartTick = now;
            d.feedsInWindow = 0;
        }

        if (d.feedsInWindow >= FEED_CAP_USES) {
            long secondsLeft = (FEED_WINDOW_TICKS - (now - d.windowStartTick)) / 20L;
            player.sendMessage(
                    Text.translatable("message.dragoncare.bond.feed_cap_wait", formatTime(secondsLeft))
                            .formatted(Formatting.YELLOW),
                    true
            );
            state.markDirty();
            sync(player, dragonId, d);
            return;
        }

        d.feedsInWindow++;
        d.points = Math.min(THRESHOLDS[MAX_LEVEL], d.points + FEED_BOND);
        d.lastFeedTick = now;

        if (d.feedsInWindow >= FEED_CAP_USES) {
            long secondsLeft = (FEED_WINDOW_TICKS - (now - d.windowStartTick)) / 20L;
            player.sendMessage(
                    Text.translatable("message.dragoncare.bond.feed_cap_reached", formatTime(secondsLeft))
                            .formatted(Formatting.GOLD),
                    false
            );
        }

        state.markDirty();
        sync(player, dragonId, d);
        applyBondEffects(dragon, getLevel(d.points));
    }

    /**
     * РЎРєР°СЂРјР»РёРІР°РЅРёРµ РѕСЃРѕР±РѕРіРѕ РєРѕСЂРјР° (РґСЂР°РєРѕРЅРёР№ С„СЂСѓРєС‚), РєРѕС‚РѕСЂС‹Р№ РґРѕР±Р°РІР»СЏРµС‚ {@code points}
     * РѕС‡РєРѕРІ РїСЂРёРІСЏР·Р°РЅРЅРѕСЃС‚Рё Р’ РћР‘РҐРћР” 10-РјРёРЅСѓС‚РЅРѕРіРѕ РєСѓР»РґР°СѓРЅР° {@link #FEED_CAP_USES}.
     * Р”РµРґСѓРї РІ РїСЂРµРґРµР»Р°С… РѕРґРЅРѕРіРѕ С‚РёРєР° СЃРѕС…СЂР°РЅСЏРµС‚СЃСЏ (NeoForge РјРѕР¶РµС‚ РІС‹Р·РІР°С‚СЊ interact РґРІР°Р¶РґС‹).
     */
    public static void onFeedBypass(ServerPlayerEntity player, EntityDragonBase dragon, int points) {
        MinecraftServer server = player.getServer();
        if (server == null || points <= 0) return;
        if (dragon.getOwnerUuid() == null || !dragon.getOwnerUuid().equals(player.getUuid())) return;

        UUID dragonId = dragon.getUuid();
        BondState state = BondState.get(server);
        long now = server.getOverworld().getTime();

        // Safety net: СЃРІРµР¶РµРІС‹Р»СѓРїРёРІС€РёР№СЃСЏ РјР°Р»С‹С€ Р±РµР· Р·Р°РїРёСЃРё Рѕ РїСЂРёРІСЏР·Р°РЅРЅРѕСЃС‚Рё
        if (state.peek(dragonId) == null && dragon.getAgeInDays() <= 1) {
            BondData seed = state.getOrCreate(dragonId);
            seed.points = EGG_HATCH_STARTING_BOND;
            seed.lastPassiveTick = now;
            state.markDirty();
        }

        BondData d = state.getOrCreate(dragonId);

        // Per-tick РґРµРґСѓРї: С‚РѕС‚ Р¶Рµ click РјРѕР¶РµС‚ РІС‹Р·РІР°С‚СЊ interact РґРІР°Р¶РґС‹ Р·Р° С‚РёРє
        if (d.lastFeedTick == now) return;

        applyPassive(d, now);

        // РљР°Рї Рё РѕРєРЅРѕ РќР• РўР РћР“РђР•Рњ вЂ” С„СЂСѓРєС‚ РёРіРЅРѕСЂРёСЂСѓРµС‚ РєСѓР»РґР°СѓРЅ
        d.points = Math.min(THRESHOLDS[MAX_LEVEL], d.points + points);
        d.lastFeedTick = now;

        state.markDirty();
        sync(player, dragonId, d);
        applyBondEffects(dragon, getLevel(d.points));
    }

    /** Owner attacked their own tamed dragon: lose {@code damage * 2} bond points. */
    public static void onDamageByOwner(ServerPlayerEntity player, EntityDragonBase dragon, float damage) {
        MinecraftServer server = player.getServer();
        if (server == null || damage <= 0f) return;
        if (dragon.getOwnerUuid() == null || !dragon.getOwnerUuid().equals(player.getUuid())) return;

        UUID dragonId = dragon.getUuid();
        BondState state = BondState.get(server);
        BondData d = state.peek(dragonId);
        if (d == null) return;

        long now = server.getOverworld().getTime();
        applyPassive(d, now);
        int loss = Math.max(1, Math.round(damage * 2f));
        d.points = Math.max(0, d.points - loss);
        state.markDirty();
        sync(player, dragonId, d);
        applyBondEffects(dragon, getLevel(d.points));
    }

    /**
     * Initialises bond when a dragon is freshly hatched from an egg by its owner.
     * Idempotent вЂ” does nothing if a record already exists for this dragon.
     */
    public static void initOnHatch(ServerPlayerEntity owner, EntityDragonBase dragon) {
        MinecraftServer server = owner.getServer();
        if (server == null) return;
        UUID dragonId = dragon.getUuid();
        BondState state = BondState.get(server);
        if (state.peek(dragonId) != null) return;

        BondData d = state.getOrCreate(dragonId);
        d.points = EGG_HATCH_STARTING_BOND;
        d.lastPassiveTick = server.getOverworld().getTime();
        state.markDirty();
        sync(owner, dragonId, d);
        applyBondEffects(dragon, getLevel(d.points));
    }

    /**
     * Initialises bond when a dragon was tamed via this addon's feeding system (level 0).
     * Idempotent вЂ” does nothing if a record already exists.
     */
    public static void initOnTame(ServerPlayerEntity owner, EntityDragonBase dragon) {
        MinecraftServer server = owner.getServer();
        if (server == null) return;
        UUID dragonId = dragon.getUuid();
        BondState state = BondState.get(server);
        if (state.peek(dragonId) != null) return;

        BondData d = state.getOrCreate(dragonId);
        d.points = 0;
        d.lastPassiveTick = server.getOverworld().getTime();
        state.markDirty();
        sync(owner, dragonId, d);
        applyBondEffects(dragon, 0);
    }

    // ---------- bond-level effects ----------

    /**
     * Applies (or refreshes) the four bond-tier status effects on the dragon as infinite,
     * hidden buffs matching {@code level}. Lower-tier effects are removed first so a level
     * drop demotes the buffs cleanly.
     */
    public static void applyBondEffects(EntityDragonBase dragon, int level) {
        int lvl = Math.max(0, Math.min(MAX_LEVEL, level));
        setOrClear(dragon, StatusEffects.RESISTANCE,   RESISTANCE_AMP_BY_LEVEL[lvl]);
        setOrClear(dragon, StatusEffects.REGENERATION, REGENERATION_AMP_BY_LEVEL[lvl]);
        setOrClear(dragon, StatusEffects.STRENGTH,     STRENGTH_AMP_BY_LEVEL[lvl]);
        // Legacy cleanup: earlier versions used a flat Health Boost effect; clear it.
        dragon.removeStatusEffect(StatusEffects.HEALTH_BOOST);
        applyHealthBonus(dragon, HEALTH_PCT_BY_LEVEL[lvl]);
    }

    /**
     * Applies (or removes) the bond max-HP attribute modifier. Uses
     * {@link EntityAttributeModifier.Operation#ADD_MULTIPLIED_BASE} so the bonus scales with
     * the dragon's stage-driven base health (5%/10% of base, not of running total).
     */
    private static void applyHealthBonus(EntityDragonBase dragon, double pct) {
        EntityAttributeInstance inst = dragon.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (inst == null) return;
        
        // Remove ALL modifiers that match our ID or path to prevent stacking of old persistent/temporary modifiers.
        for (EntityAttributeModifier modifier : new java.util.ArrayList<>(inst.getModifiers())) {
            if (modifier.getId().equals(BOND_HEALTH_MOD_ID) || "bond_health".equals(modifier.getName())) {
                inst.removeModifier(modifier.getId());
            }
        }

        if (pct > 0.0) {
            inst.addTemporaryModifier(new EntityAttributeModifier(BOND_HEALTH_MOD_ID, "bond_health", pct, EntityAttributeModifier.Operation.MULTIPLY_BASE));
        }
    }

    private static void setOrClear(EntityDragonBase dragon, StatusEffect effect, int amplifier) {
        if (amplifier < 0) {
            dragon.removeStatusEffect(effect);
            return;
        }
        StatusEffectInstance current = dragon.getStatusEffect(effect);
        if (current != null && current.getAmplifier() == amplifier && current.isInfinite()) {
            return; // already correct вЂ” avoid jitter
        }
        dragon.removeStatusEffect(effect);
        dragon.addStatusEffect(new StatusEffectInstance(effect, StatusEffectInstance.INFINITE, amplifier,
                false, false, false /* ambient, showParticles, showIcon */));
    }

    // ---------- admin / cheats ----------

    /**
     * Cheats-only mutation: add {@code delta} points (may be negative). Bypasses the
     * 10-minute feed cap and the per-tick dedup. Returns the new total points.
     */
    public static int addPointsAdmin(MinecraftServer server, EntityDragonBase dragon, int delta) {
        UUID dragonId = dragon.getUuid();
        BondState state = BondState.get(server);
        BondData d = state.getOrCreate(dragonId);
        long now = server.getOverworld().getTime();
        applyPassive(d, now);
        int max = THRESHOLDS[MAX_LEVEL];
        d.points = Math.max(0, Math.min(max, d.points + delta));
        if (d.lastPassiveTick == 0L) d.lastPassiveTick = now;
        state.markDirty();
        applyBondEffects(dragon, getLevel(d.points));
        notifyOwner(server, dragon, d);
        return d.points;
    }

    /** Cheats-only mutation: set bond points to an exact value. Returns the clamped total. */
    public static int setPointsAdmin(MinecraftServer server, EntityDragonBase dragon, int newPoints) {
        UUID dragonId = dragon.getUuid();
        BondState state = BondState.get(server);
        BondData d = state.getOrCreate(dragonId);
        int max = THRESHOLDS[MAX_LEVEL];
        d.points = Math.max(0, Math.min(max, newPoints));
        d.lastPassiveTick = server.getOverworld().getTime();
        state.markDirty();
        applyBondEffects(dragon, getLevel(d.points));
        notifyOwner(server, dragon, d);
        return d.points;
    }

    private static void notifyOwner(MinecraftServer server, EntityDragonBase dragon, BondData d) {
        UUID ownerId = dragon.getOwnerUuid();
        if (ownerId == null) return;
        ServerPlayerEntity owner = server.getPlayerManager().getPlayer(ownerId);
        if (owner != null) sync(owner, dragon.getUuid(), d);
    }
}



