package com.dragoncare.mechanics;

import com.iafenvoy.iceandfire.entity.EntityDragonBase;
import com.dragoncare.DragonCare;
import com.dragoncare.effect.ModEffects;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.ElderGuardianEntity;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.mob.WitchEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.EntityTypeTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.living.LivingEvent;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = DragonCare.MOD_ID)
@SuppressWarnings("deprecation")
public class AshPoisoningSystem {

    private static Set<Block> ASH_BLOCKS = null;
    
    // Р’СЂРµРјСЏ РЅР°С…РѕР¶РґРµРЅРёСЏ РёРіСЂРѕРєР° РІ РїРµРїР»Рµ (РІ С‚РёРєР°С…)
    private static final Map<UUID, Integer> EXPOSURE_TICKS = new ConcurrentHashMap<>();
    
    // РРіСЂРѕРєРё, СЂСЏРґРѕРј СЃ РєРѕС‚РѕСЂС‹РјРё РЅРµРґР°РІРЅРѕ Р±С‹Р» РѕР±РЅР°СЂСѓР¶РµРЅ РїРµРїРµР»
    private static final Set<UUID> RECENTLY_FOUND_ASH = ConcurrentHashMap.newKeySet();
    
    // РўР°Р№РјРµСЂ Р·Р°РґРµСЂР¶РєРё СЃРЅСЏС‚РёСЏ РїРµРїР»Р° С‚Р°Р±Р»РµС‚РєР°РјРё (С‚РёРєРё РґРѕ СЃРЅСЏС‚РёСЏ)
    private static final Map<UUID, Integer> TABLET_CURE_TICKS = new ConcurrentHashMap<>();

    // РљСЌС€: РјРѕР± -> РїРѕР·РёС†РёСЏ Р±Р»РёР¶Р°Р№С€РµРіРѕ РѕР±РЅР°СЂСѓР¶РµРЅРЅРѕРіРѕ РїРµРїР»Р°. РСЃРїРѕР»СЊР·СѓРµС‚СЃСЏ AvoidAshGoal.
    private static final Map<UUID, BlockPos> NEARBY_ASH_FOR_MOB = new ConcurrentHashMap<>();
    
    // Р¤Р»Р°Рі: РµСЃР»Рё true вЂ” С‚РµРєСѓС‰РµРµ СѓРґР°Р»РµРЅРёРµ СЌС„С„РµРєС‚Р° РІС‹Р·РІР°РЅРѕ РЅР°С€РµР№ СЃРёСЃС‚РµРјРѕР№, РЅРµ Р±Р»РѕРєРёСЂРѕРІР°С‚СЊ
    private static boolean REMOVING_BY_SYSTEM = false;

    private static void initAshBlocks() {
        if (ASH_BLOCKS != null) return;
        ASH_BLOCKS = new HashSet<>();
        // NOTE: Ice and Fire CE registers the charred blocks with a single 'r'
        // ("chared_*") вЂ” this is a typo in the upstream mod, not here.
        String[] charredNames = {
                "ash",
                "chared_dirt",
                "chared_dirt_path",
                "chared_grass",
                "chared_stone",
                "chared_cobblestone",
                "chared_gravel"
        };
        for (String name : charredNames) {
            Block b = Registries.BLOCK.get(Identifier.of("iceandfire", name));
            if (b != net.minecraft.block.Blocks.AIR) {
                ASH_BLOCKS.add(b);
            }
        }
    }

    /**
     * Returns {@code true} if the given entity can take damage from standing in ash.
     * Mirrors the exclusion list used by {@link #onEntityTick(LivingEvent.LivingTickEvent)} so
     * that the avoidance AI goal targets the same population.
     */
    public static boolean isVulnerableToAsh(LivingEntity entity) {
        if (entity instanceof PlayerEntity) return false;
        if (!(entity instanceof net.minecraft.entity.mob.MobEntity)) return false;

        if (entity instanceof WitherEntity
                || entity instanceof EnderDragonEntity
                || entity instanceof ElderGuardianEntity
                || entity instanceof WardenEntity
                || entity instanceof WitchEntity
                || entity instanceof EntityDragonBase) {
            return false;
        }

        Identifier typeId = Registries.ENTITY_TYPE.getId(entity.getType());
        if (typeId.getNamespace().equals("iceandfire")) {
            String path = typeId.getPath();
            if (path.contains("skull") || path.contains("statue") || path.contains("ghost") || path.contains("dragon")) {
                return false;
            }
        }

        if (entity.isUndead()) return false;

        return true;
    }

    @SubscribeEvent
    public static void onEntityTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        World world = entity.getWorld();
        
        // РўРѕР»СЊРєРѕ РЅР° СЃРµСЂРІРµСЂРµ
        if (world.isClient()) return;
        
        boolean isPlayer = entity instanceof PlayerEntity;
        UUID uuid = entity.getUuid();
        
        int tickInterval = 40;
        if (isPlayer) {
            // Р›РµРЅРёРІРѕРµ СЃРєР°РЅРёСЂРѕРІР°РЅРёРµ СЂР°Р· РІ 10 СЃРµРє (200 С‚РёРєРѕРІ), РїРѕРєР° РЅРµС‚ РїРµРїР»Р° СЂСЏРґРѕРј Рё РЅРµС‚ СЌС„С„РµРєС‚Р°
            boolean isActive = EXPOSURE_TICKS.containsKey(uuid) || entity.hasStatusEffect(ModEffects.ASH_POISONING.get()) || RECENTLY_FOUND_ASH.contains(uuid);
            tickInterval = isActive ? 40 : 200;
        } else {
            // Р›РµРЅРёРІРѕРµ СЃРєР°РЅРёСЂРѕРІР°РЅРёРµ РґР»СЏ РјРѕР±РѕРІ СЂР°Р· РІ 5 СЃРµРє (100 С‚РёРєРѕРІ), РµСЃР»Рё РїРµРїР»Р° РЅРµС‚ СЂСЏРґРѕРј
            tickInterval = RECENTLY_FOUND_ASH.contains(uuid) ? 40 : 100;
        }
        
        // РћРїС‚РёРјРёР·Р°С†РёСЏ 1: РџСЂРѕРІРµСЂСЏРµРј РјРѕР±РѕРІ Рё РёРіСЂРѕРєРѕРІ РІ Р·Р°РІРёСЃРёРјРѕСЃС‚Рё РѕС‚ СЃРѕСЃС‚РѕСЏРЅРёСЏ Р»РµРЅРёРІРѕРіРѕ СЃРєР°РЅРёСЂРѕРІР°РЅРёСЏ
        if ((entity.getId() + entity.age) % tickInterval != 0) return;
        
        if (!com.dragoncare.config.AddonConfig.ASH_POISONING_ENABLED.get()) return;
        
        initAshBlocks();
        if (ASH_BLOCKS.isEmpty()) return;

        // РџСЂРµРґРІР°СЂРёС‚РµР»СЊРЅС‹Р№ С„РёР»СЊС‚СЂ РёСЃРєР»СЋС‡РµРЅРёР№
        if (entity instanceof WitherEntity || 
            entity instanceof EnderDragonEntity || 
            entity instanceof ElderGuardianEntity || 
            entity instanceof WardenEntity || 
            entity instanceof WitchEntity ||
            entity instanceof EntityDragonBase) {
            return;
        }

        Identifier typeId = Registries.ENTITY_TYPE.getId(entity.getType());
        String namespace = typeId.getNamespace();
        String path = typeId.getPath();
        
        // Exclude specific non-living Ice and Fire entities that extend MobEntity
        if (namespace.equals("iceandfire")) {
            if (path.contains("skull") || path.contains("statue") || path.contains("ghost") || path.contains("dragon")) {
                return;
            }
        }
        
        // РСЃРєР»СЋС‡РµРЅРёРµ РЅРµР¶РёС‚Рё
        if (entity.isUndead()) {
            return;
        }

        // Exclude non-mobs and non-players (e.g. ArmorStands, Dragon Skulls, etc.)
        if (!isPlayer && !(entity instanceof net.minecraft.entity.mob.MobEntity)) {
            return;
        }
        
        // Exclude creative and spectator players
        if (isPlayer) {
            PlayerEntity player = (PlayerEntity) entity;
            if (player.isCreative() || player.isSpectator()) {
                return;
            }
        }

        int minStage1 = com.dragoncare.config.AddonConfig.ASH_STAGE_1.get();
        int minStage2 = com.dragoncare.config.AddonConfig.ASH_STAGE_2.get();
        int minStage3 = com.dragoncare.config.AddonConfig.ASH_STAGE_3.get();
        int minStage4 = com.dragoncare.config.AddonConfig.ASH_STAGE_4.get();

        int ashCount = 0;
        boolean foundAsh = false;

        BlockPos center = entity.getBlockPos();
        int radius = 10;
        
        int minX = center.getX() - radius;
        int maxX = center.getX() + radius;
        int minY = Math.max(world.getBottomY(), center.getY() - radius);
        int maxY = Math.min(world.getTopY() - 1, center.getY() + radius);
        int minZ = center.getZ() - radius;
        int maxZ = center.getZ() + radius;

        // РћРїС‚РёРјРёР·Р°С†РёСЏ 3: РСЃРїРѕР»СЊР·РѕРІР°РЅРёРµ MutableBlockPos РёР·Р±Р°РІР»СЏРµС‚ РѕС‚ СЃРѕР·РґР°РЅРёСЏ С‚С‹СЃСЏС‡ РѕР±СЉРµРєС‚РѕРІ
        BlockPos.Mutable pos = new BlockPos.Mutable();
        
        // РЎРєР°РЅРёСЂРѕРІР°РЅРёРµ РєСѓР±Р° 21x21x21
        outer:
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                // РЈСЃС‚Р°РЅР°РІР»РёРІР°РµРј Y = 0 РїСЂРѕСЃС‚Рѕ РґР»СЏ РїСЂРѕРІРµСЂРєРё Р·Р°РіСЂСѓР·РєРё С‡Р°РЅРєР° (X Рё Z РґРѕСЃС‚Р°С‚РѕС‡РЅРѕ)
                pos.set(x, 0, z);
                
                // РР·Р±РµРіР°РµРј Р·Р°РіСЂСѓР·РєРё С‡Р°РЅРєРѕРІ - РµСЃР»Рё С‡Р°РЅРє РЅРµ Р·Р°РіСЂСѓР¶РµРЅ, РїСЂРѕРїСѓСЃРєР°РµРј РІРµСЃСЊ (X, Z) СЃС‚РѕР»Р±РµС†
                if (!world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
                    // РџРµСЂРµРїСЂС‹РіРёРІР°РµРј Р·Р° РїСЂРµРґРµР»С‹ С‚РµРєСѓС‰РµРіРѕ С‡Р°РЅРєР° РїРѕ Z
                    z = (z | 15);
                    continue;
                }

                // Р§Р°РЅРє РєРѕР»РѕРЅРєРё РїРѕР»СѓС‡Р°РµРј РѕРґРёРЅ СЂР°Р· вЂ” getBlockState РЅРёР¶Рµ Р±РѕР»СЊС€Рµ
                // РЅРµ РґРµР»Р°РµС‚ РїРѕРІС‚РѕСЂРЅС‹Р№ РїРѕРёСЃРє С‡Р°РЅРєР° РЅР° РєР°Р¶РґС‹Р№ РёР· 21 Р±Р»РѕРєР° РїРѕ Y.
                net.minecraft.world.chunk.Chunk columnChunk = world.getChunk(x >> 4, z >> 4);

                for (int y = minY; y <= maxY; y++) {
                    pos.set(x, y, z);

                    BlockState state = columnChunk.getBlockState(pos);
                    if (ASH_BLOCKS.contains(state.getBlock())) {
                        ashCount++;
                        foundAsh = true;
                        
                        // РћРїС‚РёРјРёР·Р°С†РёСЏ 2: Р Р°РЅРЅРёР№ РІС‹С…РѕРґ РґР»СЏ РјРѕР±РѕРІ. 
                        if (!isPlayer) {
                            break outer;
                        }
                        
                        // РћРїС‚РёРјРёР·Р°С†РёСЏ 4: Р Р°РЅРЅРёР№ РІС‹С…РѕРґ РґР»СЏ РёРіСЂРѕРєРѕРІ
                        if (ashCount >= minStage4) {
                            break outer;
                        }
                    }
                }
            }
        }

        if (foundAsh) {
            RECENTLY_FOUND_ASH.add(uuid);
        } else {
            RECENTLY_FOUND_ASH.remove(uuid);
        }

        if (!isPlayer) {
            if (foundAsh) {
                // Р—Р°РїРѕРјРёРЅР°РµРј РїРѕР·РёС†РёСЋ РґР»СЏ AvoidAshGoal вЂ” СЃРєР°РЅРµСЂ СѓР¶Рµ РЅР°С€С‘Р» Р±Р»РѕРє,
                // РЅРµ РЅР°РґРѕ СЃРєР°РЅРёСЂРѕРІР°С‚СЊ РїРѕРІС‚РѕСЂРЅРѕ РІ РіРѕР°Р»Рµ.
                NEARBY_ASH_FOR_MOB.put(uuid, pos.toImmutable());
                // Р”Р»СЏ РјРѕР±РѕРІ: РїР°СЃСЃРёРІРЅС‹Р№ СѓСЂРѕРЅ 1 РµРґ. (Р°РЅРёРјР°С†РёСЏ СЂР°Р· РІ 40/100 С‚РёРєРѕРІ)
                entity.damage(entity.getDamageSources().magic(), 1.0f);
            } else {
                NEARBY_ASH_FOR_MOB.remove(uuid);
            }
            return;
        }

        // --- Р›РћР“РРљРђ Р”Р›РЇ РР“Р РћРљРђ ---

        // РћС‡РёС‰Р°РµРј РІСЂРµРјСЏ РІРѕР·РґРµР№СЃС‚РІРёСЏ, РµСЃР»Рё РёРіСЂРѕРє РїРѕР»РЅРѕСЃС‚СЊСЋ РІС‹Р»РµС‡РёР»СЃСЏ (СЌС„С„РµРєС‚ СЃРїР°Р» РµСЃС‚РµСЃС‚РІРµРЅРЅС‹Рј РїСѓС‚РµРј)
        if (!foundAsh && !entity.hasStatusEffect(ModEffects.ASH_POISONING.get())) {
            EXPOSURE_TICKS.remove(uuid);
            return;
        }

        // Р•СЃР»Рё РёРіСЂРѕРє РїРѕРґ С‚Р°Р±Р»РµС‚РєР°РјРё - Р·Р°РїСѓСЃРєР°РµРј РѕС‚Р»РѕР¶РµРЅРЅРѕРµ СЃРЅСЏС‚РёРµ РїРµРїР»Р°
        if (entity.hasStatusEffect(ModEffects.MYSTERIOUS_TABLETS.get())) {
            EXPOSURE_TICKS.remove(uuid);
            
            if (entity.hasStatusEffect(ModEffects.ASH_POISONING.get())) {
                // Р—Р°РїСѓСЃРєР°РµРј С‚Р°Р№РјРµСЂ СЃРЅСЏС‚РёСЏ, РµСЃР»Рё РµС‰С‘ РЅРµ Р·Р°РїСѓС‰РµРЅ (3-4 СЃРµРєСѓРЅРґС‹ = 60-80 С‚РёРєРѕРІ)
                if (!TABLET_CURE_TICKS.containsKey(uuid)) {
                    TABLET_CURE_TICKS.put(uuid, 70); // ~3.5 СЃРµРє
                } else {
                    int remaining = TABLET_CURE_TICKS.get(uuid) - 40; // РІС‹С‡РёС‚Р°РµРј РёРЅС‚РµСЂРІР°Р» С‚РёРєР° (40)
                    if (remaining <= 0) {
                        // Р’СЂРµРјСЏ РІС‹С€Р»Рѕ вЂ” СЃРЅРёРјР°РµРј СЌС„С„РµРєС‚ С‡РµСЂРµР· С„Р»Р°Рі РѕР±С…РѕРґР°
                        TABLET_CURE_TICKS.remove(uuid);
                        REMOVING_BY_SYSTEM = true;
                        try {
                            entity.removeStatusEffect(ModEffects.ASH_POISONING.get());
                        } finally {
                            REMOVING_BY_SYSTEM = false;
                        }
                    } else {
                        TABLET_CURE_TICKS.put(uuid, remaining);
                    }
                }
            } else {
                TABLET_CURE_TICKS.remove(uuid);
            }
            return;
        } else {
            // РўР°Р±Р»РµС‚РѕРє РЅРµС‚ вЂ” СЃР±СЂР°СЃС‹РІР°РµРј С‚Р°Р№РјРµСЂ РµСЃР»Рё Р±С‹Р»
            TABLET_CURE_TICKS.remove(uuid);
        }

        if (ashCount < minStage1) {
            // РњРµРЅСЊС€Рµ РЅСѓР¶РЅРѕРіРѕ РјРёРЅРёРјСѓРјР° Р±Р»РѕРєРѕРІ - РЅРµ РѕС‚СЂР°РІР»СЏРµРј, РЅРѕ СЌС„С„РµРєС‚ РµС‰Рµ РјРѕР¶РµС‚ РІРёСЃРµС‚СЊ
            return;
        }

        // РРіСЂРѕРє РІ РїРµРїР»Рµ - СѓРІРµР»РёС‡РёРІР°РµРј СЃС‡РµС‚С‡РёРє РїСЂРµР±С‹РІР°РЅРёСЏ (РЅР° 40 С‚РёРєРѕРІ)
        int currentExposure = Math.min(EXPOSURE_TICKS.getOrDefault(uuid, 0) + 40, 72000);
        EXPOSURE_TICKS.put(uuid, currentExposure);

        int exposureMinutes = currentExposure / (20 * 60);
        int stage = 1;

        if (ashCount >= minStage4) {
            stage = 2 + exposureMinutes; // РџРѕРІС‹С€Р°РµС‚СЃСЏ РєР°Р¶РґСѓСЋ РјРёРЅСѓС‚Сѓ
        } else if (ashCount >= minStage3) {
            stage = 1 + (exposureMinutes / 4);
        } else if (ashCount >= minStage2) {
            stage = 1 + (exposureMinutes / 7);
        } else if (ashCount >= minStage1) {
            stage = 1 + (exposureMinutes / 10);
        }

        stage = Math.min(4, stage);

        // Р”Р»РёС‚РµР»СЊРЅРѕСЃС‚СЊ Р·Р°РІРёСЃРёС‚ РѕС‚ СЃС‚Р°РґРёРё (15, 40, 65, 90 СЃРµРєСѓРЅРґ)
        int durationTicks = (15 + (stage - 1) * 25) * 20;
        
        // РќР°РєР»Р°РґС‹РІР°РµРј СЌС„С„РµРєС‚. Amplifier: 0 = СЃС‚Р°РґРёСЏ 1, 1 = СЃС‚Р°РґРёСЏ 2, 2 = СЃС‚Р°РґРёСЏ 3, 3 = СЃС‚Р°РґРёСЏ 4
        // Р§С‚РѕР±С‹ РјРѕР»РѕРєРѕ РЅРµ РїРѕРјРѕРіР°Р»Рѕ, РІ 1.21 РёСЃРїРѕР»СЊР·СѓРµС‚СЃСЏ РєР°СЃС‚РѕРјРЅС‹Р№ С‚РµРі. РџРѕРєР° С‡С‚Рѕ СЌС„С„РµРєС‚ РїСЂРѕСЃС‚Рѕ Р±СѓРґРµС‚
        // РјРѕРјРµРЅС‚Р°Р»СЊРЅРѕ РЅР°РєР»Р°РґС‹РІР°С‚СЊСЃСЏ Р·Р°РЅРѕРІРѕ, РµСЃР»Рё РёРіСЂРѕРє РІСЃРµ РµС‰Рµ РІ РїРµРїР»Рµ.
        entity.addStatusEffect(new StatusEffectInstance(ModEffects.ASH_POISONING.get(), durationTicks, stage - 1, false, false, false));
    }

    @SubscribeEvent
    public static void onEffectRemove(MobEffectEvent.Remove event) {
        if (event.getEffect() != ModEffects.ASH_POISONING.get() || REMOVING_BY_SYSTEM) return;
        // Never force-keep the effect on a dead/removed entity вЂ” that just
        // pins a useless effect onto a corpse. Curing is still gated to our
        // own tablet logic for living players (milk / arbitrary removal stay
        // blocked, as intended).
        LivingEntity living = event.getEntity();
        if (!living.isAlive() || living.isRemoved()) return;
        event.setCanceled(true);
    }

    public static void onPlayerLoggedOut(UUID uuid) {
        EXPOSURE_TICKS.remove(uuid);
        RECENTLY_FOUND_ASH.remove(uuid);
        TABLET_CURE_TICKS.remove(uuid);
    }

    /** Returns the last ash position the scanner detected near this mob, or {@code null}. */
    public static BlockPos getNearbyAshFor(UUID mobUuid) {
        return NEARBY_ASH_FOR_MOB.get(mobUuid);
    }

    /** Cleanup when a mob is unloaded or removed so the cache doesn't grow unbounded. */
    @SubscribeEvent
    public static void onEntityLeave(net.minecraftforge.event.entity.EntityLeaveLevelEvent event) {
        // Skip client-side: dropped items / particles / projectiles spam this event every tick.
        if (event.getLevel().isClient()) return;
        UUID id = event.getEntity().getUuid();
        NEARBY_ASH_FOR_MOB.remove(id);
        RECENTLY_FOUND_ASH.remove(id);
    }

    public static void clearCache() { EXPOSURE_TICKS.clear(); RECENTLY_FOUND_ASH.clear(); TABLET_CURE_TICKS.clear(); NEARBY_ASH_FOR_MOB.clear(); }
}



