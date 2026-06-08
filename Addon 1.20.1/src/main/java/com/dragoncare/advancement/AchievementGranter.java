package com.dragoncare.advancement;

import com.dragoncare.DragonCare;
import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Centralised helper for granting code-driven (impossible-trigger) advancements
 * registered by this addon. Each constant matches a JSON file under
 * {@code data/dragoncare/advancement/}.
 */
public final class AchievementGranter {

    public static final Identifier ROOT =
            Identifier.of(DragonCare.MOD_ID, "root");
    public static final Identifier DRAGON_BEFRIENDED =
            Identifier.of(DragonCare.MOD_ID, "dragon_befriended");
    public static final Identifier TAMING_STARTED =
            Identifier.of(DragonCare.MOD_ID, "taming_started");
    public static final Identifier TAMED_FIRE_DRAGON =
            Identifier.of(DragonCare.MOD_ID, "tamed_fire_dragon");
    public static final Identifier TAMED_ICE_DRAGON =
            Identifier.of(DragonCare.MOD_ID, "tamed_ice_dragon");
    public static final Identifier TAMED_LIGHTNING_DRAGON =
            Identifier.of(DragonCare.MOD_ID, "tamed_lightning_dragon");
    public static final Identifier ALL_HUNTER_DIARIES =
            Identifier.of(DragonCare.MOD_ID, "all_hunter_diaries");
    public static final Identifier TAMED_THREE_ELEMENTS =
            Identifier.of(DragonCare.MOD_ID, "tamed_three_elements");
    public static final Identifier DRACOMANIA =
            Identifier.of(DragonCare.MOD_ID, "dracomania");
    public static final Identifier FRESH_LOOK =
            Identifier.of(DragonCare.MOD_ID, "fresh_look");
    public static final Identifier HERCULES_FEAT =
            Identifier.of(DragonCare.MOD_ID, "hercules_feat");
    public static final Identifier DRAGON_BRUSH_CRAFT =
            Identifier.of(DragonCare.MOD_ID, "dragon_brush_craft");

    private AchievementGranter() {}

    /**
     * Grants all unobtained criteria of the given advancement to the player.
     * Silently no-ops if the advancement is already done or not yet loaded.
     */
    public static void grant(ServerPlayerEntity player, Identifier id) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        Advancement adv = server.getAdvancementLoader().get(id);
        if (adv == null) return;
        PlayerAdvancementTracker tracker = player.getAdvancementTracker();
        AdvancementProgress progress = tracker.getProgress(adv);
        boolean wasDone = progress.isDone();
        if (!wasDone) {
            for (String name : progress.getUnobtainedCriteria()) {
                tracker.grantCriterion(adv, name);
            }
        }

        // Check if one of the core elements was granted, then check for three elements gate unlock
        if (id.equals(TAMED_FIRE_DRAGON) || id.equals(TAMED_ICE_DRAGON) || id.equals(TAMED_LIGHTNING_DRAGON)) {
            checkAndUnlockThreeElements(player);
        }
    }

    /**
     * Grants a specific criterion of the given advancement to the player.
     */
    public static void grantCriterion(ServerPlayerEntity player, Identifier id, String criterion) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        Advancement adv = server.getAdvancementLoader().get(id);
        if (adv == null) return;
        PlayerAdvancementTracker tracker = player.getAdvancementTracker();
        tracker.grantCriterion(adv, criterion);
    }

    /**
     * Checks if the player has tamed fire, ice, and lightning dragons, and if so, unlocks the hidden gate.
     */
    public static void checkAndUnlockThreeElements(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        PlayerAdvancementTracker tracker = player.getAdvancementTracker();

        Advancement fire = server.getAdvancementLoader().get(TAMED_FIRE_DRAGON);
        Advancement ice = server.getAdvancementLoader().get(TAMED_ICE_DRAGON);
        Advancement lightning = server.getAdvancementLoader().get(TAMED_LIGHTNING_DRAGON);

        if (fire != null && ice != null && lightning != null) {
            boolean hasFire = tracker.getProgress(fire).isDone();
            boolean hasIce = tracker.getProgress(ice).isDone();
            boolean hasLightning = tracker.getProgress(lightning).isDone();

            if (hasFire && hasIce && hasLightning) {
                grant(player, TAMED_THREE_ELEMENTS);
            }
        }
    }
}


