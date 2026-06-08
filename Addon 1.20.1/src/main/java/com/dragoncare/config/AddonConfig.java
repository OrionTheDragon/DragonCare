package com.dragoncare.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.HashMap;
import java.util.Map;

public class AddonConfig {

    private static final String I18N = "dragoncare.config.";

    private static final Map<String, ForgeConfigSpec.IntValue> STAGE_VALUES = new HashMap<>();

    public static final ForgeConfigSpec SPEC;

    // Scale Shears (per stage)
    public static final ForgeConfigSpec.IntValue SHEARS_S2_MIN, SHEARS_S2_MAX, SHEARS_S2_CD;
    public static final ForgeConfigSpec.IntValue SHEARS_S3_MIN, SHEARS_S3_MAX, SHEARS_S3_CD;
    public static final ForgeConfigSpec.IntValue SHEARS_S4_MIN, SHEARS_S4_MAX, SHEARS_S4_CD;
    public static final ForgeConfigSpec.IntValue SHEARS_S5_MIN, SHEARS_S5_MAX, SHEARS_S5_CD;

    // Syringe (per stage)
    public static final ForgeConfigSpec.IntValue SYRINGE_S1_USES, SYRINGE_S1_WIN, SYRINGE_S1_CD, SYRINGE_S1_MIN, SYRINGE_S1_MAX;
    public static final ForgeConfigSpec.IntValue SYRINGE_S2_USES, SYRINGE_S2_WIN, SYRINGE_S2_CD, SYRINGE_S2_MIN, SYRINGE_S2_MAX;
    public static final ForgeConfigSpec.IntValue SYRINGE_S3_USES, SYRINGE_S3_WIN, SYRINGE_S3_CD, SYRINGE_S3_MIN, SYRINGE_S3_MAX;
    public static final ForgeConfigSpec.IntValue SYRINGE_S4_USES, SYRINGE_S4_WIN, SYRINGE_S4_CD, SYRINGE_S4_MIN, SYRINGE_S4_MAX;
    public static final ForgeConfigSpec.IntValue SYRINGE_S5_USES, SYRINGE_S5_WIN, SYRINGE_S5_CD, SYRINGE_S5_MIN, SYRINGE_S5_MAX;

    // Painkiller
    public static final ForgeConfigSpec.IntValue PAINKILLER_CD;
    public static final ForgeConfigSpec.IntValue PAINKILLER_DURABILITY;

    // Dragon
    public static final ForgeConfigSpec.IntValue DRAGON_MAX_HEALTH;
    public static final ForgeConfigSpec.BooleanValue WILD_DRAGONS_AGE;
    public static final ForgeConfigSpec.BooleanValue TAMED_DRAGONS_AGE;
    public static final ForgeConfigSpec.BooleanValue PREVENT_DRAGON_FIGHT_BABIES;
    public static final ForgeConfigSpec.BooleanValue PREVENT_DRAGON_FIGHT_ALL;

    // Dragon AI & Spawning
    public static final ForgeConfigSpec.BooleanValue DISABLE_BABY_PANIC;
    public static final ForgeConfigSpec.BooleanValue DISABLE_NEW_BABY_AI;
    public static final ForgeConfigSpec.BooleanValue DISABLE_WILD_BABY_SPAWNS;
    public static final ForgeConfigSpec.BooleanValue MOTHER_PRIORITY_OVER_FOOD;
    public static final ForgeConfigSpec.IntValue ROOST_SPAWN_RATE;

    // Taming
    public static final ForgeConfigSpec.IntValue TAMING_DURATION_SECONDS;
    public static final ForgeConfigSpec.DoubleValue TAMING_PENALTY_MULTIPLIER;

    // Dragon Dirt
    public static final ForgeConfigSpec.BooleanValue DIRT_ENABLED_TAMED;
    public static final ForgeConfigSpec.BooleanValue DIRT_ENABLED_WILD;
    public static final ForgeConfigSpec.DoubleValue DIRT_SPEED_DAYS;
    public static final ForgeConfigSpec.BooleanValue DIRT_EASY_MINIGAME;

    // Ash Poisoning
    public static final ForgeConfigSpec.BooleanValue ASH_POISONING_ENABLED;
    public static final ForgeConfigSpec.BooleanValue ASH_SENSOR_SOUND_ENABLED;
    public static final ForgeConfigSpec.IntValue ASH_STAGE_1;
    public static final ForgeConfigSpec.IntValue ASH_STAGE_2;
    public static final ForgeConfigSpec.IntValue ASH_STAGE_3;
    public static final ForgeConfigSpec.IntValue ASH_STAGE_4;

    // Dragon Fruit
    public static final ForgeConfigSpec.IntValue DRAGON_FRUIT_STEM_GROWTH_CHANCE;
    public static final ForgeConfigSpec.IntValue DRAGON_FRUIT_SPAWN_CHANCE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        // ===== Scale Shears =====
        b.comment("Obsidian-Diamond Shears settings (per dragon growth stage)")
                .translation(I18N + "scale_shears")
                .push("scale_shears");
            shearsStage(b, "stage_2", 5, 10, 300);
            shearsStage(b, "stage_3", 25, 45, 210);
            shearsStage(b, "stage_4", 46, 64, 120);
            shearsStage(b, "stage_5", 65, 85, 60);
        b.pop();

        // ===== Syringe =====
        b.comment("Syringe settings: max collections in a window, then cooldown")
                .translation(I18N + "syringe")
                .push("syringe");
            syringeStage(b, "stage_1", 3, 1200, 1200);
            syringeStage(b, "stage_2", 6, 1020, 1020);
            syringeStage(b, "stage_3", 12, 900, 900);
            syringeStage(b, "stage_4", 18, 720, 720);
            syringeStage(b, "stage_5", 24, 540, 540);
        b.pop();

        // ===== Painkiller =====
        b.comment("Dragon Painkiller settings")
                .translation(I18N + "painkiller")
                .push("painkiller");
        PAINKILLER_CD = b.comment("Default cooldown after a single dose, in seconds")
                .translation(I18N + "painkiller.cooldown_seconds")
                .defineInRange("cooldown_seconds", 120, 0, 7200);
        PAINKILLER_DURABILITY = b.comment("Durability (uses). Requires a game restart to take effect.")
                .translation(I18N + "painkiller.durability")
                .defineInRange("durability", 60, 1, 10000);
        b.pop();

        // ===== Dragon =====
        b.comment("Dragon attribute overrides")
                .translation(I18N + "dragon")
                .push("dragon");
        DRAGON_MAX_HEALTH = b.comment(
                        "Max HP an adult (125+ days) dragon will have.",
                        "This addon also lifts the vanilla 1024 cap on max health, so values above 1024 take effect.",
                        "Set to 0 to disable this override and keep Ice and Fire CE's own dragon.maxHealth value (still uncapped).")
                .translation(I18N + "dragon.max_health")
                .defineInRange("max_health", 0, 0, 10_000_000);
        WILD_DRAGONS_AGE = b.comment(
                        "If true, wild dragons will age naturally over time (like tamed ones).",
                        "If false, Ice and Fire CE's default behavior applies (wild dragons' aging is disabled).")
                .translation(I18N + "dragon.wild_dragons_age")
                .define("wild_dragons_age", true);
        TAMED_DRAGONS_AGE = b.comment(
                        "If true, tamed and neutral (befriended) dragons will age passively over time.",
                        "CE disables aging for tamed dragons by default. This option restores it.",
                        "Note: the Sickly Dragon Meal item that forces growth is not affected by this setting.")
                .translation(I18N + "dragon.tamed_dragons_age")
                .define("tamed_dragons_age", true);
        PREVENT_DRAGON_FIGHT_BABIES = b.comment(
                        "If true, baby and juvenile dragons (Stages 1 & 2) of different elements will never fight each other,",
                        "and older dragons (Stage 3+) of different elements will not attack baby and juvenile dragons of another element.",
                        "Default is true.")
                .translation(I18N + "dragon.prevent_dragon_fight_babies")
                .define("prevent_dragon_fight_babies", true);
        PREVENT_DRAGON_FIGHT_ALL = b.comment(
                        "If true, ALL dragons of different elements will never attack or damage each other, regardless of their growth stage.",
                        "If this option is enabled, the baby-only prevention setting becomes inactive and automatically turns off to avoid logical conflicts.",
                        "Default is false.")
                .translation(I18N + "dragon.prevent_dragon_fight_all")
                .define("prevent_dragon_fight_all", false);

        // ===== Dragon AI & Spawning =====
        b.comment("Settings for new baby dragon AI and wild spawning mechanics")
                .translation(I18N + "dragon.ai_spawning")
                .push("ai_spawning");
        DISABLE_BABY_PANIC = b.comment(
                        "If true, wild baby dragons will not flee from players in survival mode.",
                        "Note: They will still not attack players (based on hunting targets), they just won't run away.",
                        "Default is false (fleeing is enabled).")
                .translation(I18N + "dragon.ai_spawning.disable_baby_panic")
                .define("disable_baby_panic", false);
        DISABLE_NEW_BABY_AI = b.comment(
                        "If true, completely disables all new AI mechanics for baby dragons (fleeing, following adults, etc.).",
                        "Reverts baby dragons to their vanilla Ice and Fire behavior.",
                        "Default is false (new AI is enabled).")
                .translation(I18N + "dragon.ai_spawning.disable_new_baby_ai")
                .define("disable_new_baby_ai", false);
        DISABLE_WILD_BABY_SPAWNS = b.comment(
                        "If true, disables the natural generation of wild baby dragons in roosts, caves, and biomes.",
                        "Default is false (wild babies will spawn).")
                .translation(I18N + "dragon.ai_spawning.disable_wild_baby_spawns")
                .define("disable_wild_baby_spawns", false);
        MOTHER_PRIORITY_OVER_FOOD = b.comment(
                        "If true, baby dragons will prioritize following their mother over players holding food.",
                        "If false, they will stop following the mother when tempted with Exceptional Dragon Meal.",
                        "Default is false.")
                .translation(I18N + "dragon.ai_spawning.mother_priority_over_food")
                .define("mother_priority_over_food", false);
        ROOST_SPAWN_RATE = b.comment(
                        "How frequently baby dragons spawn in surface roosts.",
                        "A value of N means 1 baby spawns every N roosts.",
                        "Min 1 (100% chance), Max 10 (10% chance). Default is 6.")
                .translation(I18N + "dragon.ai_spawning.roost_spawn_rate")
                .defineInRange("roost_spawn_rate", 6, 1, 10);
        b.pop();

        // ===== Taming =====
        b.comment("Dragon Taming settings")
                .translation(I18N + "dragon.taming")
                .push("taming");
        TAMING_DURATION_SECONDS = b.comment(
                        "How long the taming process takes (in seconds).",
                        "Default is 180 seconds (3 minutes).")
                .translation(I18N + "dragon.taming.duration_seconds")
                .defineInRange("duration_seconds", 180, 40, 3600);
        TAMING_PENALTY_MULTIPLIER = b.comment(
                        "Multiplier applied to the remaining taming time if the player attacks the dragon and breaks trust.",
                        "Default is 2.0 (time doubles).")
                .translation(I18N + "dragon.taming.penalty_multiplier")
                .defineInRange("penalty_multiplier", 2.0D, 1.5D, 10.0D);
        b.pop();

        // ===== Dragon Dirt =====
        b.comment("Dragon dirtiness settings")
                .translation(I18N + "dragon.dirt")
                .push("dirt");
        DIRT_ENABLED_TAMED = b.comment(
                        "If true, tamed dragons will gradually get dirty over time.")
                .translation(I18N + "dragon.dirt.enabled_tamed")
                .define("enabled_tamed", true);
        DIRT_ENABLED_WILD = b.comment(
                        "If true, wild/passive dragons will also get dirty.",
                        "WARNING: Enabling this may cause performance drops as the server will",
                        "have to process dirt accumulation for all wild dragons.")
                .translation(I18N + "dragon.dirt.enabled_wild")
                .define("enabled_wild", false);
        DIRT_SPEED_DAYS = b.comment(
                        "How many in-game days it takes for a dragon's dirtiness to increase by 1 level.")
                .translation(I18N + "dragon.dirt.speed_days")
                .defineInRange("speed_days", 1.0D, 0.5D, 30.0D);
        DIRT_EASY_MINIGAME = b.comment(
                        "If true, simplifies the QTE mini-game for dragon cleaning.",
                        "By default false. When true, Stage 1 dirtiness bypasses the mini-game completely,",
                        "and Stages 2-5 will have their target zones scaled up in size by 1.5x.")
                .translation(I18N + "dragon.dirt.easy_minigame")
                .define("easy_minigame", false);
        b.pop();
        
        b.pop();

        // ===== Ash Poisoning =====
        b.comment("Ash Poisoning settings")
                .translation(I18N + "ash_poisoning")
                .push("ash_poisoning");
        ASH_POISONING_ENABLED = b.comment(
                        "If true, players will get ash poisoning when near ash blocks.",
                        "If false, the Ash Sensor is also fully disabled — it stops scanning",
                        "and stops playing its idle/geiger sounds to avoid wasting cycles on a",
                        "feature the player has turned off.")
                .translation(I18N + "ash_poisoning.enabled")
                .define("enabled", true);
        ASH_SENSOR_SOUND_ENABLED = b.comment(
                        "If true, the Ash Sensor plays its idle hum and geiger tracks while held.",
                        "Setting this to false silences the sensor while still letting it scan and",
                        "show the level on its model. Has no effect if ash poisoning itself is disabled,",
                        "in which case the sensor is fully disabled and silent regardless of this option.")
                .translation(I18N + "ash_poisoning.sensor_sound_enabled")
                .define("sensor_sound_enabled", true);
        ASH_STAGE_1 = b.comment("Minimum ash blocks required for Stage 1 poisoning")
                .translation(I18N + "ash_poisoning.stage_1_min")
                .defineInRange("stage_1_min", 10, 1, 9261);
        ASH_STAGE_2 = b.comment("Minimum ash blocks required for Stage 2 poisoning")
                .translation(I18N + "ash_poisoning.stage_2_min")
                .defineInRange("stage_2_min", 36, 1, 9261);
        ASH_STAGE_3 = b.comment("Minimum ash blocks required for Stage 3 poisoning")
                .translation(I18N + "ash_poisoning.stage_3_min")
                .defineInRange("stage_3_min", 61, 1, 9261);
        ASH_STAGE_4 = b.comment("Minimum ash blocks required for Stage 4 poisoning")
                .translation(I18N + "ash_poisoning.stage_4_min")
                .defineInRange("stage_4_min", 85, 1, 9261);
        b.pop();

        // ===== Dragon Fruit =====
        b.comment("Dragon Fruit growth settings")
                .translation(I18N + "dragon_fruit")
                .push("dragon_fruit");
        DRAGON_FRUIT_STEM_GROWTH_CHANCE = b.comment(
                        "Chance (%) that a stem growth tick will succeed.",
                        "100 = same speed as vanilla melon/pumpkin, 50 = twice as slow.",
                        "Lower values make the stem grow slower.")
                .translation(I18N + "dragon_fruit.stem_growth_chance")
                .defineInRange("stem_growth_chance", 50, 1, 100);
        DRAGON_FRUIT_SPAWN_CHANCE = b.comment(
                        "Chance (%) that a mature stem will produce a fruit on each tick.",
                        "100 = same speed as vanilla melon/pumpkin, 50 = twice as slow.",
                        "Lower values make the fruit appear less frequently.")
                .translation(I18N + "dragon_fruit.fruit_spawn_chance")
                .defineInRange("fruit_spawn_chance", 50, 1, 100);
        b.pop();

        SPEC = b.build();

        SHEARS_S2_MIN = STAGE_VALUES.get("scale_shears.stage_2.min_scales");
        SHEARS_S2_MAX = STAGE_VALUES.get("scale_shears.stage_2.max_scales");
        SHEARS_S2_CD  = STAGE_VALUES.get("scale_shears.stage_2.cooldown_seconds");
        SHEARS_S3_MIN = STAGE_VALUES.get("scale_shears.stage_3.min_scales");
        SHEARS_S3_MAX = STAGE_VALUES.get("scale_shears.stage_3.max_scales");
        SHEARS_S3_CD  = STAGE_VALUES.get("scale_shears.stage_3.cooldown_seconds");
        SHEARS_S4_MIN = STAGE_VALUES.get("scale_shears.stage_4.min_scales");
        SHEARS_S4_MAX = STAGE_VALUES.get("scale_shears.stage_4.max_scales");
        SHEARS_S4_CD  = STAGE_VALUES.get("scale_shears.stage_4.cooldown_seconds");
        SHEARS_S5_MIN = STAGE_VALUES.get("scale_shears.stage_5.min_scales");
        SHEARS_S5_MAX = STAGE_VALUES.get("scale_shears.stage_5.max_scales");
        SHEARS_S5_CD  = STAGE_VALUES.get("scale_shears.stage_5.cooldown_seconds");

        SYRINGE_S1_USES = STAGE_VALUES.get("syringe.stage_1.max_uses");
        SYRINGE_S1_WIN  = STAGE_VALUES.get("syringe.stage_1.window_seconds");
        SYRINGE_S1_CD   = STAGE_VALUES.get("syringe.stage_1.cooldown_seconds");
        SYRINGE_S1_MIN  = STAGE_VALUES.get("syringe.stage_1.blood_min");
        SYRINGE_S1_MAX  = STAGE_VALUES.get("syringe.stage_1.blood_max");
        SYRINGE_S2_USES = STAGE_VALUES.get("syringe.stage_2.max_uses");
        SYRINGE_S2_WIN  = STAGE_VALUES.get("syringe.stage_2.window_seconds");
        SYRINGE_S2_CD   = STAGE_VALUES.get("syringe.stage_2.cooldown_seconds");
        SYRINGE_S2_MIN  = STAGE_VALUES.get("syringe.stage_2.blood_min");
        SYRINGE_S2_MAX  = STAGE_VALUES.get("syringe.stage_2.blood_max");
        SYRINGE_S3_USES = STAGE_VALUES.get("syringe.stage_3.max_uses");
        SYRINGE_S3_WIN  = STAGE_VALUES.get("syringe.stage_3.window_seconds");
        SYRINGE_S3_CD   = STAGE_VALUES.get("syringe.stage_3.cooldown_seconds");
        SYRINGE_S3_MIN  = STAGE_VALUES.get("syringe.stage_3.blood_min");
        SYRINGE_S3_MAX  = STAGE_VALUES.get("syringe.stage_3.blood_max");
        SYRINGE_S4_USES = STAGE_VALUES.get("syringe.stage_4.max_uses");
        SYRINGE_S4_WIN  = STAGE_VALUES.get("syringe.stage_4.window_seconds");
        SYRINGE_S4_CD   = STAGE_VALUES.get("syringe.stage_4.cooldown_seconds");
        SYRINGE_S4_MIN  = STAGE_VALUES.get("syringe.stage_4.blood_min");
        SYRINGE_S4_MAX  = STAGE_VALUES.get("syringe.stage_4.blood_max");
        SYRINGE_S5_USES = STAGE_VALUES.get("syringe.stage_5.max_uses");
        SYRINGE_S5_WIN  = STAGE_VALUES.get("syringe.stage_5.window_seconds");
        SYRINGE_S5_CD   = STAGE_VALUES.get("syringe.stage_5.cooldown_seconds");
        SYRINGE_S5_MIN  = STAGE_VALUES.get("syringe.stage_5.blood_min");
        SYRINGE_S5_MAX  = STAGE_VALUES.get("syringe.stage_5.blood_max");
    }

    private static void shearsStage(ForgeConfigSpec.Builder b, String stageKey, int min, int max, int cdSec) {
        b.translation(I18N + "scale_shears." + stageKey).push(stageKey);
        STAGE_VALUES.put("scale_shears." + stageKey + ".min_scales",
                b.comment("Minimum scales per shearing")
                        .translation(I18N + "scale_shears.min_scales")
                        .defineInRange("min_scales", min, 0, 256));
        STAGE_VALUES.put("scale_shears." + stageKey + ".max_scales",
                b.comment("Maximum scales per shearing")
                        .translation(I18N + "scale_shears.max_scales")
                        .defineInRange("max_scales", max, 0, 256));
        STAGE_VALUES.put("scale_shears." + stageKey + ".cooldown_seconds",
                b.comment("Cooldown in seconds")
                        .translation(I18N + "scale_shears.cooldown_seconds")
                        .defineInRange("cooldown_seconds", cdSec, 0, 7200));
        b.pop();
    }

    private static void syringeStage(ForgeConfigSpec.Builder b, String stageKey, int uses, int winSec, int cdSec) {
        b.translation(I18N + "syringe." + stageKey).push(stageKey);
        STAGE_VALUES.put("syringe." + stageKey + ".max_uses",
                b.comment("Max blood collections within the window before cooldown")
                        .translation(I18N + "syringe.max_uses")
                        .defineInRange("max_uses", uses, 1, 1000));
        STAGE_VALUES.put("syringe." + stageKey + ".window_seconds",
                b.comment("Window in seconds")
                        .translation(I18N + "syringe.window_seconds")
                        .defineInRange("window_seconds", winSec, 1, 7200));
        STAGE_VALUES.put("syringe." + stageKey + ".cooldown_seconds",
                b.comment("Cooldown in seconds once max_uses is reached")
                        .translation(I18N + "syringe.cooldown_seconds")
                        .defineInRange("cooldown_seconds", cdSec, 0, 7200));
        STAGE_VALUES.put("syringe." + stageKey + ".blood_min",
                b.comment("Minimum blood items per collection")
                        .translation(I18N + "syringe.blood_min")
                        .defineInRange("blood_min", 1, 1, 64));
        STAGE_VALUES.put("syringe." + stageKey + ".blood_max",
                b.comment("Maximum blood items per collection")
                        .translation(I18N + "syringe.blood_max")
                        .defineInRange("blood_max", 1, 1, 64));
        b.pop();
    }

    public static int ticksFromSeconds(ForgeConfigSpec.IntValue secondsValue) {
        return Math.max(0, secondsValue.get()) * 20;
    }
}


