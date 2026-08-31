package com.dragoncare.event;

import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import com.dragoncare.DragonCare;
import com.dragoncare.command.BondCommands;
import com.dragoncare.command.ModCommands;
import com.dragoncare.effect.ModEffects;
import com.dragoncare.taming.BondManager;
import com.dragoncare.taming.BondState;
import com.dragoncare.taming.DragonTamingManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = DragonCare.MOD_ID)
public class ModEvents {

    /** Fallback if {@code IafCommonConfig.dragon.maxTamedDragonAge} is unavailable. */
    private static final int FALLBACK_MAX_TAMED_AGE = 128;

    /**
     * Pending hatch-init queue: dragon UUID -> ticks remaining.
     * CE's egg sets {@code setTamed/setOwnerUuid} AFTER {@code spawnEntity}, so neither
     * EntityJoinLevelEvent nor StartTracking nor a same-tick {@code server.execute(...)}
     * sees the dragon as tamed. We poll once per tick for a short window until tamed=true.
     */
    private static final Map<UUID, Integer> PENDING_HATCH = new ConcurrentHashMap<>();
    private static final int HATCH_WAIT_TICKS = 20; // 1 second

    @SubscribeEvent
    public static void onLivingDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (!event.getEntity().getWorld().isClient && event.getEntity() instanceof DragonBaseEntity dragon) {
            com.dragoncare.mechanics.DragonFamilyManager.onDragonDeath(dragon);
            UUID dragonId = dragon.getUuid();
            com.dragoncare.item.ScaleShearsItem.clearDragon(dragonId);
            com.dragoncare.item.SyringeItem.clearDragon(dragonId);
            com.dragoncare.item.DragonPainkillerItem.clearDragon(dragonId);
            // Прунинг по гибели: освобождаем сохранённые данные погибшего дракона.
            // (Раньше периодический cleanupStaleEntries удалял данные и у ЖИВЫХ драконов
            //  в выгруженных чанках, теряя связь/грязь питомцев — теперь чистим только по смерти.)
            MinecraftServer server = dragon.getServer();
            if (server != null) {
                BondState.get(server).remove(dragonId);
                com.dragoncare.mechanics.DragonDirtState.get(server).remove(dragonId);
            }
        }
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();

        // Painkilling effect halves all incoming damage on dragons
        if (victim instanceof DragonBaseEntity dragon && dragon.hasStatusEffect(ModEffects.PAINKILLING)) {
            event.setAmount(event.getAmount() * 0.5F);
        }

        Entity attacker = event.getSource().getAttacker();

        // Prevent damage between dragons of different elements based on config
        if (attacker instanceof DragonBaseEntity attackerDragon && victim instanceof DragonBaseEntity victimDragon) {
            // Prevent family infighting (e.g. mother breathes fire, accidentally hits baby)
            if (com.dragoncare.mechanics.DragonFamilyManager.isFamily(attackerDragon, victimDragon)) {
                event.setCanceled(true);
                return;
            }

            if (attackerDragon.getClass() != victimDragon.getClass()) {
                if (com.dragoncare.config.AddonConfig.PREVENT_DRAGON_FIGHT_ALL.get()) {
                    event.setCanceled(true);
                    return;
                }
                if (com.dragoncare.config.AddonConfig.PREVENT_DRAGON_FIGHT_BABIES.get()) {
                    int stage1 = attackerDragon.getDragonStage();
                    int stage2 = victimDragon.getDragonStage();
                    if (stage1 <= 2 || stage2 <= 2) {
                        event.setCanceled(true);
                        return;
                    }
                }
            }
        }

        // A befriended dragon never hurts its befriender
        if (attacker instanceof DragonBaseEntity dragon && victim instanceof PlayerEntity player) {
            MinecraftServer server = player.getServer();
            if (server != null && DragonTamingManager.isBefriended(server, dragon.getUuid(), player.getUuid())) {
                event.setCanceled(true);
            }
        }

        // Player hits a dragon: betrayal handler + bond loss when own dragon
        if (victim instanceof DragonBaseEntity dragon
                && attacker instanceof ServerPlayerEntity player
                && !player.getWorld().isClient) {
            DragonTamingManager.onPlayerHitDragon(player, dragon, event.getAmount());
            if (dragon.getOwnerUuid() != null && dragon.getOwnerUuid().equals(player.getUuid())) {
                BondManager.onDamageByOwner(player, dragon, event.getAmount());
            }
        }
    }

    /** Prevent a befriended dragon from acquiring its befriender as a target, and handle cross-element dragon fights. */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof DragonBaseEntity dragon)) return;
        
        net.minecraft.entity.LivingEntity target = event.getNewAboutToBeSetTarget();
        if (target == null) return;

        // Prevent target acquisition between dragons of different elements based on config
        if (target instanceof DragonBaseEntity targetDragon) {
            if (dragon.getClass() != targetDragon.getClass()) {
                if (com.dragoncare.config.AddonConfig.PREVENT_DRAGON_FIGHT_ALL.get()) {
                    event.setNewAboutToBeSetTarget(null);
                    return;
                }
                if (com.dragoncare.config.AddonConfig.PREVENT_DRAGON_FIGHT_BABIES.get()) {
                    int stage1 = dragon.getDragonStage();
                    int stage2 = targetDragon.getDragonStage();
                    if (stage1 <= 2 || stage2 <= 2) {
                        event.setNewAboutToBeSetTarget(null);
                        return;
                    }
                }
            }
            return;
        }

        // Hunting restrictions for baby (Stage 1) and juvenile (Stage 2) dragons
        if (!(target instanceof PlayerEntity)) {
            int stage = dragon.getDragonStage();
            if (stage <= 2 && !isSuitablePrey(stage, target)) {
                event.setNewAboutToBeSetTarget(null);
                return;
            }
        }

        if (target instanceof PlayerEntity player) {
            // Untamed dragons under 50 days are not aggressive to players
            if (!dragon.isTamed() && dragon.getAgeInDays() < 50) {
                event.setNewAboutToBeSetTarget(null);
                return;
            }

            MinecraftServer server = player.getServer();
            if (server == null) return;
            if (DragonTamingManager.isBefriended(server, dragon.getUuid(), player.getUuid())) {
                event.setNewAboutToBeSetTarget(null);
            }
        }
    }

    /** Drives active taming sessions + processes pending hatch initialisations. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        DragonTamingManager.tick(event.getServer());
        com.dragoncare.command.DragonAnimationDebugCommands.tick(event.getServer());
        processPendingHatches(event.getServer());
        com.dragoncare.mechanics.OrphanSpawner.tick(event.getServer().getOverworld());
        
        long currentTick = event.getServer().getOverworld().getTime();
        
        if (currentTick % 1200 == 0) { // Every 1 minute
            com.dragoncare.item.ScaleShearsItem.clearExpired(currentTick);
            com.dragoncare.item.SyringeItem.clearExpired(currentTick);
            com.dragoncare.item.DragonPainkillerItem.clearExpired(currentTick);
        }
        
    }

    /** Registers brigadier commands ({@code /dragonbond}, {@code /attachmentinfo}). */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        BondCommands.register(event.getDispatcher());
        ModCommands.register(event.getDispatcher());
        com.dragoncare.command.DirtCommands.register(event.getDispatcher());
        com.dragoncare.command.WoundDebugCommands.register(event.getDispatcher());
        com.dragoncare.command.DragonAnimationDebugCommands.register(event.getDispatcher());
    }

    /** Cleanup memory in AshPoisoningSystem when a player logs out. */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        com.dragoncare.mechanics.AshPoisoningSystem.onPlayerLoggedOut(event.getEntity().getUuid());
        com.dragoncare.command.DragonAnimationDebugCommands.clearForPlayer(event.getEntity().getUuid());
    }

    /** Ensure Dragon Care root advancement is always granted on login. */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayerEntity player) {
            com.dragoncare.advancement.AchievementGranter.grant(player,
                    com.dragoncare.advancement.AchievementGranter.ROOT);
            com.dragoncare.advancement.AchievementGranter.checkAndUnlockThreeElements(player);
        }
    }

    /** Clear transient state when the server stops (prevents stale references on world switch). */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING_HATCH.clear();
        com.dragoncare.advancement.HunterDiaryTracker.clearCache();
        com.dragoncare.dragonphone.PhoneGlowTickHandler.clearCache();
        com.dragoncare.item.DragonPainkillerItem.clearCache();
        com.dragoncare.item.ScaleShearsItem.clearCache();
        com.dragoncare.item.SyringeItem.clearCache();
        com.dragoncare.mechanics.AshPoisoningSystem.clearCache();
        com.dragoncare.taming.DragonTamingManager.clearCache();
        com.dragoncare.command.DragonAnimationDebugCommands.clear();
    }

    private static void processPendingHatches(MinecraftServer server) {
        if (PENDING_HATCH.isEmpty()) return;

        Iterator<Map.Entry<UUID, Integer>> it = PENDING_HATCH.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> e = it.next();
            UUID dragonId = e.getKey();
            int remaining = e.getValue();

            DragonBaseEntity dragon = findDragon(server, dragonId);
            if (dragon == null || dragon.isRemoved()) {
                it.remove();
                continue;
            }

            if (dragon.isTamed() && dragon.getOwnerUuid() != null) {
                if (BondState.get(server).peek(dragonId) == null) {
                    ServerPlayerEntity owner = server.getPlayerManager().getPlayer(dragon.getOwnerUuid());
                    if (owner != null) {
                        BondManager.initOnHatch(owner, dragon);
                    } else {
                        BondState state = BondState.get(server);
                        com.dragoncare.taming.BondData d = state.getOrCreate(dragonId);
                        d.points = BondManager.EGG_HATCH_STARTING_BOND;
                        d.lastPassiveTick = server.getOverworld().getTime();
                        state.markDirty();
                        BondManager.applyBondEffects(dragon, BondManager.getLevel(d.points));
                    }
                }
                it.remove();
                continue;
            }

            if (remaining <= 1) {
                it.remove();
            } else {
                e.setValue(remaining - 1);
            }
        }
    }

    private static DragonBaseEntity findDragon(MinecraftServer server, UUID dragonId) {
        for (ServerWorld world : server.getWorlds()) {
            Entity ent = world.getEntity(dragonId);
            if (ent instanceof DragonBaseEntity d) return d;
        }
        return null;
    }

    /**
     * Owner feeds {@code iceandfire:dragon_meal} to their tamed dragon:
     *   - always grant bond points (subject to 36/10min cap),
     *   - if dragon is fully grown, CE rejects the meal — handle consumption + hunger ourselves
     *     and cancel the event so the meal still "works" cosmetically.
     */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClient) return;
        if (!(event.getTarget() instanceof DragonBaseEntity dragon)) return;
        if (!(event.getEntity() instanceof ServerPlayerEntity player)) return;

        ItemStack stack = event.getItemStack();
        Identifier itemId = net.minecraft.registry.Registries.ITEM.getId(stack.getItem());
        if (stack.isEmpty() || !itemId.toString().equals("iceandfire:dragon_meal")) return;
        if (dragon.getOwnerUuid() == null || !dragon.getOwnerUuid().equals(player.getUuid())) return;

        // Always grant bond on a feed attempt by the owner
        BondManager.onFeed(player, dragon);

        // For fully-grown tamed dragons, CE's interactMob rejects dragon_meal.
        // Handle consumption + hunger ourselves and cancel CE's logic.
        if (dragon.getAgeInDays() >= getMaxTamedAge()) {
            if (!player.isCreative()) stack.decrement(1);
            try {
                int newHunger = Math.min(100, dragon.getHunger() + 20);
                dragon.setHunger(newHunger);
            } catch (Throwable ignored) {
                // unknown setHunger contract on some CE forks — bond was the main goal
            }
            event.setCanceled(true);
            event.setCancellationResult(ActionResult.SUCCESS);
        }
    }

    /** Sync bond to a player's client when they start tracking one of their dragons. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof DragonBaseEntity dragon)) return;
        if (!(event.getEntity() instanceof ServerPlayerEntity player)) return;

        // Sync dirt to everyone tracking
        com.dragoncare.mechanics.DragonDirtManager.syncTo(player, dragon);

        if (dragon.getOwnerUuid() == null || !dragon.getOwnerUuid().equals(player.getUuid())) return;

        // Catch-up logic for Dracomania and base taming achievements
        String variant = dragon.getVariant();
        if (variant != null && !variant.isBlank()) {
            com.dragoncare.advancement.AchievementGranter.grantCriterion(player, com.dragoncare.advancement.AchievementGranter.DRACOMANIA, variant);
        }
        if (dragon instanceof com.iafenvoy.iceandfire.entity.FireDragonEntity) {
            com.dragoncare.advancement.AchievementGranter.grant(player, com.dragoncare.advancement.AchievementGranter.TAMED_FIRE_DRAGON);
        } else if (dragon instanceof com.iafenvoy.iceandfire.entity.IceDragonEntity) {
            com.dragoncare.advancement.AchievementGranter.grant(player, com.dragoncare.advancement.AchievementGranter.TAMED_ICE_DRAGON);
        } else if (dragon instanceof com.iafenvoy.iceandfire.entity.LightningDragonEntity) {
            com.dragoncare.advancement.AchievementGranter.grant(player, com.dragoncare.advancement.AchievementGranter.TAMED_LIGHTNING_DRAGON);
        }

        // Lazy fallback for baby dragons whose tick-poll window expired before they
        // became visible to the owner (e.g. log-out/log-in across hatch).
        MinecraftServer server = player.getServer();
        if (server != null && dragon.getAgeInDays() <= 1
                && BondState.get(server).peek(dragon.getUuid()) == null) {
            BondManager.initOnHatch(player, dragon);
            return;
        }

        BondManager.syncTo(player, dragon);
        // Re-assert bond effects in case the dragon's NBT-saved effects drifted.
        if (server != null) {
            int level = BondManager.getLevel(BondManager.getPoints(server, dragon.getUuid()));
            BondManager.applyBondEffects(dragon, level);
        }
    }

    /**
     * Enqueue freshly spawned baby dragons for tick-polled hatch init.
     *
     * <p>CE's {@code DragonEggEntity#updateEggCondition} calls {@code spawnEntity()}
     * BEFORE {@code setTamed()} and {@code setOwnerUuid()} on the same tick. Both
     * {@code EntityJoinLevelEvent} and {@code PlayerEvent.StartTracking} fire on this
     * same tick, AND {@code MinecraftServer#execute} runs synchronously when called
     * from the server thread — so all in-tick paths see the dragon as untamed. We
     * therefore enqueue the UUID and poll it once per tick from {@code onServerTick}
     * until tamed/owner are set (or the timeout window expires).
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClient) return;

        // Attach the flee-from-ash goal to any vulnerable land-pathing mob.
        if (com.dragoncare.config.AddonConfig.ASH_POISONING_ENABLED.get() && event.getEntity() instanceof net.minecraft.entity.mob.PathAwareEntity mob
                && com.dragoncare.mechanics.AshPoisoningSystem.isVulnerableToAsh(mob)) {
            mob.goalSelector.getGoals().removeIf(g -> g.getGoal() instanceof com.dragoncare.mechanics.AvoidAshGoal);
            mob.goalSelector.add(2, new com.dragoncare.mechanics.AvoidAshGoal(mob, 1.25D));
        }

        if (!(event.getEntity() instanceof DragonBaseEntity dragon)) return;

        if (!com.dragoncare.config.AddonConfig.DISABLE_NEW_BABY_AI.get()) {
            dragon.goalSelector.getGoals().removeIf(g -> 
                g.getGoal() instanceof com.dragoncare.mechanics.ai.BabyDragonFollowAdultGoal ||
                g.getGoal() instanceof com.dragoncare.mechanics.ai.WildDragonTemptGoal ||
                g.getGoal() instanceof com.dragoncare.mechanics.ai.WildDragonFleePlayerGoal ||
                g.getGoal() instanceof com.dragoncare.mechanics.ai.ParentProtectBabiesGoal
            );
            // Add follow goal for all baby/juvenile dragons (both tamed and wild)
            dragon.goalSelector.add(4, new com.dragoncare.mechanics.ai.BabyDragonFollowAdultGoal(dragon, 1.15D));
            
            // Add Tempt goal for all baby dragons (both tamed and wild)
            dragon.goalSelector.add(3, new com.dragoncare.mechanics.ai.WildDragonTemptGoal(dragon, 1.0D));

            if (!dragon.isTamed()) {
                // Add Flee goals for untamed dragons
                if (!com.dragoncare.config.AddonConfig.DISABLE_BABY_PANIC.get()) {
                    dragon.goalSelector.add(2, new com.dragoncare.mechanics.ai.WildDragonFleePlayerGoal(dragon, 16.0D, 1.5D));
                }
            }
            
            // Add parent protection goal for adult dragons (checks stage internally)
            dragon.goalSelector.add(3, new com.dragoncare.mechanics.ai.ParentProtectBabiesGoal(dragon, 32.0D, 1.0D));
        }

        MinecraftServer server = dragon.getServer();
        if (server != null) {
            // Apply dirt effects
            int dirtLvl = com.dragoncare.mechanics.DragonDirtManager.getDirtLevel(server, dragon.getUuid());
            com.dragoncare.mechanics.DragonDirtManager.applyDirtEffects(dragon, dirtLvl);
        }

        // Re-assert bond effects on join if the dragon is tamed and we are on server
        if (dragon.isTamed()) {
            if (server != null) {
                int points = BondManager.getPoints(server, dragon.getUuid());
                BondManager.applyBondEffects(dragon, BondManager.getLevel(points));
            }
        }

        if (event.loadedFromDisk()) return;
        if (dragon.getAgeInDays() > 1) return;

        PENDING_HATCH.put(dragon.getUuid(), HATCH_WAIT_TICKS);
    }

    /** Intercept taming to immediately start the dirt timer. */
    @SubscribeEvent
    public static void onAnimalTame(net.neoforged.neoforge.event.entity.living.AnimalTameEvent event) {
        if (!event.getEntity().getWorld().isClient && event.getEntity() instanceof DragonBaseEntity dragon) {
            com.dragoncare.mechanics.DragonDirtManager.tickDragon(dragon);
        }
    }

    /** Resolved-once handle to CE's {@code maxTamedDragonAge} config entry. */
    private static Object cachedAgeEntry;
    private static java.lang.reflect.Method cachedAgeGetValue;
    private static boolean ageReflectionResolved;

    private static int getMaxTamedAge() {
        try {
            // Resolve the reflection chain (Class.forName + 3 field lookups)
            // only once — afterwards each feed just invokes getValue(), which
            // still reflects the live config value.
            if (!ageReflectionResolved) {
                ageReflectionResolved = true;
                Class<?> cfgClass = Class.forName("com.iafenvoy.iceandfire.config.IafCommonConfig");
                Object instance = cfgClass.getField("INSTANCE").get(null);
                Object dragonCfg = instance.getClass().getField("dragon").get(instance);
                Object entry = dragonCfg.getClass().getField("maxTamedDragonAge").get(dragonCfg);
                cachedAgeGetValue = entry.getClass().getMethod("getValue");
                cachedAgeEntry = entry;
            }
            if (cachedAgeEntry != null) {
                Object value = cachedAgeGetValue.invoke(cachedAgeEntry);
                return ((Number) value).intValue();
            }
        } catch (Throwable t) {
            // unknown CE API shape — fall through to the fallback constant
        }
        return FALLBACK_MAX_TAMED_AGE;
    }

    private static boolean isSuitablePrey(int stage, net.minecraft.entity.LivingEntity target) {
        if (stage >= 3) {
            return true;
        }

        // Small prey: chickens, rabbits, foxes, parrots, cats, ocelots, frogs, bats, silverfish
        // and any baby animals (AnimalEntity that is baby)
        boolean isSmallPrey = target instanceof net.minecraft.entity.passive.ChickenEntity
                || target instanceof net.minecraft.entity.passive.RabbitEntity
                || target instanceof net.minecraft.entity.passive.FoxEntity
                || target instanceof net.minecraft.entity.passive.ParrotEntity
                || target instanceof net.minecraft.entity.passive.CatEntity
                || target instanceof net.minecraft.entity.passive.OcelotEntity
                || target instanceof net.minecraft.entity.passive.FrogEntity
                || target instanceof net.minecraft.entity.passive.BatEntity
                || target instanceof net.minecraft.entity.mob.SilverfishEntity
                || (target instanceof net.minecraft.entity.passive.AnimalEntity animal && animal.isBaby());

        if (stage == 1) {
            return isSmallPrey;
        }

        // Stage 2: medium prey
        if (stage == 2) {
            // Allows all small prey
            if (isSmallPrey) {
                return true;
            }

            // Allows pigs, sheep, donkeys, mules
            if (target instanceof net.minecraft.entity.passive.PigEntity
                    || target instanceof net.minecraft.entity.passive.SheepEntity
                    || target instanceof net.minecraft.entity.passive.DonkeyEntity
                    || target instanceof net.minecraft.entity.passive.MuleEntity) {
                return true;
            }

            // Allows baby cows, horses, llamas, goats (but not adults!)
            if (target instanceof net.minecraft.entity.passive.CowEntity
                    || target instanceof net.minecraft.entity.passive.HorseEntity
                    || target instanceof net.minecraft.entity.passive.LlamaEntity
                    || target instanceof net.minecraft.entity.passive.GoatEntity) {
                return ((net.minecraft.entity.passive.AnimalEntity) target).isBaby();
            }
        }

        return false;
    }
}
