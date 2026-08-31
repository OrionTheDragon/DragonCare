package com.dragoncare.mechanics;

import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = DragonCare.MOD_ID)
public class AshPoisoningSystem {

    private static Set<Block> ASH_BLOCKS = null;
    
    // Время нахождения игрока в пепле (в тиках)
    private static final Map<UUID, Integer> EXPOSURE_TICKS = new ConcurrentHashMap<>();
    
    // Игроки, рядом с которыми недавно был обнаружен пепел
    private static final Set<UUID> RECENTLY_FOUND_ASH = ConcurrentHashMap.newKeySet();
    
    // Таймер задержки снятия пепла таблетками (тики до снятия)
    private static final Map<UUID, Integer> TABLET_CURE_TICKS = new ConcurrentHashMap<>();

    // Кэш: моб -> позиция ближайшего обнаруженного пепла. Используется AvoidAshGoal.
    private static final Map<UUID, BlockPos> NEARBY_ASH_FOR_MOB = new ConcurrentHashMap<>();
    
    // Флаг: если true — текущее удаление эффекта вызвано нашей системой, не блокировать
    private static boolean REMOVING_BY_SYSTEM = false;

    private static void initAshBlocks() {
        if (ASH_BLOCKS != null) return;
        ASH_BLOCKS = new HashSet<>();
        // NOTE: Ice and Fire CE registers the charred blocks with a single 'r'
        // ("chared_*") — this is a typo in the upstream mod, not here.
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
     * Mirrors the exclusion list used by {@link #onEntityTick(EntityTickEvent.Post)} so
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
                || entity instanceof DragonBaseEntity) {
            return false;
        }

        Identifier typeId = Registries.ENTITY_TYPE.getId(entity.getType());
        if (typeId.getNamespace().equals("iceandfire")) {
            String path = typeId.getPath();
            if (path.contains("skull") || path.contains("statue") || path.contains("ghost") || path.contains("dragon")) {
                return false;
            }
        }

        if (entity.getType().isIn(EntityTypeTags.UNDEAD)) return false;

        return true;
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity rawEntity = event.getEntity();
        World world = rawEntity.getWorld();
        
        // Только на сервере
        if (world.isClient()) return;
        if (!(rawEntity instanceof LivingEntity entity)) return;
        
        boolean isPlayer = entity instanceof PlayerEntity;
        UUID uuid = entity.getUuid();
        
        int tickInterval = 40;
        if (isPlayer) {
            // Ленивое сканирование раз в 10 сек (200 тиков), пока нет пепла рядом и нет эффекта
            boolean isActive = EXPOSURE_TICKS.containsKey(uuid) || entity.hasStatusEffect(ModEffects.ASH_POISONING) || RECENTLY_FOUND_ASH.contains(uuid);
            tickInterval = isActive ? 40 : 200;
        } else {
            // Ленивое сканирование для мобов раз в 5 сек (100 тиков), если пепла нет рядом
            tickInterval = RECENTLY_FOUND_ASH.contains(uuid) ? 40 : 100;
        }
        
        // Оптимизация 1: Проверяем мобов и игроков в зависимости от состояния ленивого сканирования
        if ((entity.getId() + entity.age) % tickInterval != 0) return;
        
        if (!com.dragoncare.config.AddonConfig.ASH_POISONING_ENABLED.get()) return;
        
        initAshBlocks();
        if (ASH_BLOCKS.isEmpty()) return;

        // Предварительный фильтр исключений
        if (entity instanceof WitherEntity || 
            entity instanceof EnderDragonEntity || 
            entity instanceof ElderGuardianEntity || 
            entity instanceof WardenEntity || 
            entity instanceof WitchEntity ||
            entity instanceof DragonBaseEntity) {
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
        
        // Исключение нежити
        if (entity.getType().isIn(EntityTypeTags.UNDEAD)) {
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

        // Оптимизация 3: Использование MutableBlockPos избавляет от создания тысяч объектов
        BlockPos.Mutable pos = new BlockPos.Mutable();
        
        // Сканирование куба 21x21x21
        outer:
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                // Устанавливаем Y = 0 просто для проверки загрузки чанка (X и Z достаточно)
                pos.set(x, 0, z);
                
                // Избегаем загрузки чанков - если чанк не загружен, пропускаем весь (X, Z) столбец
                if (!world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) {
                    // Перепрыгиваем за пределы текущего чанка по Z
                    z = (z | 15);
                    continue;
                }

                // Чанк колонки получаем один раз — getBlockState ниже больше
                // не делает повторный поиск чанка на каждый из 21 блока по Y.
                net.minecraft.world.chunk.Chunk columnChunk = world.getChunk(x >> 4, z >> 4);

                for (int y = minY; y <= maxY; y++) {
                    pos.set(x, y, z);

                    BlockState state = columnChunk.getBlockState(pos);
                    if (ASH_BLOCKS.contains(state.getBlock())) {
                        ashCount++;
                        foundAsh = true;
                        
                        // Оптимизация 2: Ранний выход для мобов. 
                        if (!isPlayer) {
                            break outer;
                        }
                        
                        // Оптимизация 4: Ранний выход для игроков
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
                // Запоминаем позицию для AvoidAshGoal — сканер уже нашёл блок,
                // не надо сканировать повторно в гоале.
                NEARBY_ASH_FOR_MOB.put(uuid, pos.toImmutable());
                // Для мобов: пассивный урон 1 ед. (анимация раз в 40/100 тиков)
                entity.damage(entity.getDamageSources().magic(), 1.0f);
            } else {
                NEARBY_ASH_FOR_MOB.remove(uuid);
            }
            return;
        }

        // --- ЛОГИКА ДЛЯ ИГРОКА ---

        // Очищаем время воздействия, если игрок полностью вылечился (эффект спал естественным путем)
        if (!foundAsh && !entity.hasStatusEffect(ModEffects.ASH_POISONING)) {
            EXPOSURE_TICKS.remove(uuid);
            return;
        }

        // Если игрок под таблетками - запускаем отложенное снятие пепла
        if (entity.hasStatusEffect(ModEffects.MYSTERIOUS_TABLETS)) {
            EXPOSURE_TICKS.remove(uuid);
            
            if (entity.hasStatusEffect(ModEffects.ASH_POISONING)) {
                // Запускаем таймер снятия, если ещё не запущен (3-4 секунды = 60-80 тиков)
                if (!TABLET_CURE_TICKS.containsKey(uuid)) {
                    TABLET_CURE_TICKS.put(uuid, 70); // ~3.5 сек
                } else {
                    int remaining = TABLET_CURE_TICKS.get(uuid) - 40; // вычитаем интервал тика (40)
                    if (remaining <= 0) {
                        // Время вышло — снимаем эффект через флаг обхода
                        TABLET_CURE_TICKS.remove(uuid);
                        REMOVING_BY_SYSTEM = true;
                        try {
                            entity.removeStatusEffect(ModEffects.ASH_POISONING);
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
            // Таблеток нет — сбрасываем таймер если был
            TABLET_CURE_TICKS.remove(uuid);
        }

        if (ashCount < minStage1) {
            // Меньше нужного минимума блоков - не отравляем, но эффект еще может висеть
            return;
        }

        // Игрок в пепле - увеличиваем счетчик пребывания (на 40 тиков)
        int currentExposure = Math.min(EXPOSURE_TICKS.getOrDefault(uuid, 0) + 40, 72000);
        EXPOSURE_TICKS.put(uuid, currentExposure);

        int exposureMinutes = currentExposure / (20 * 60);
        int stage = 1;

        if (ashCount >= minStage4) {
            stage = 2 + exposureMinutes; // Повышается каждую минуту
        } else if (ashCount >= minStage3) {
            stage = 1 + (exposureMinutes / 4);
        } else if (ashCount >= minStage2) {
            stage = 1 + (exposureMinutes / 7);
        } else if (ashCount >= minStage1) {
            stage = 1 + (exposureMinutes / 10);
        }

        stage = Math.min(4, stage);

        // Длительность зависит от стадии (15, 40, 65, 90 секунд)
        int durationTicks = (15 + (stage - 1) * 25) * 20;
        
        // Накладываем эффект. Amplifier: 0 = стадия 1, 1 = стадия 2, 2 = стадия 3, 3 = стадия 4
        // Чтобы молоко не помогало, в 1.21 используется кастомный тег. Пока что эффект просто будет
        // моментально накладываться заново, если игрок все еще в пепле.
        entity.addStatusEffect(new StatusEffectInstance(ModEffects.ASH_POISONING, durationTicks, stage - 1));
    }

    @SubscribeEvent
    public static void onEffectRemove(MobEffectEvent.Remove event) {
        if (event.getEffect() != ModEffects.ASH_POISONING || REMOVING_BY_SYSTEM) return;
        // Never force-keep the effect on a dead/removed entity — that just
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
    public static void onEntityLeave(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event) {
        // Skip client-side: dropped items / particles / projectiles spam this event every tick.
        if (event.getLevel().isClient()) return;
        UUID id = event.getEntity().getUuid();
        NEARBY_ASH_FOR_MOB.remove(id);
        RECENTLY_FOUND_ASH.remove(id);
    }

    public static void clearCache() { EXPOSURE_TICKS.clear(); RECENTLY_FOUND_ASH.clear(); TABLET_CURE_TICKS.clear(); NEARBY_ASH_FOR_MOB.clear(); }
}
