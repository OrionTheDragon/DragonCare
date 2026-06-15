package com.dragoncare;

import com.dragoncare.config.AddonConfig;
import com.dragoncare.dragonphone.ModDataComponents;
import com.dragoncare.effect.ModEffects;
import com.dragoncare.item.ModItems;
import com.dragoncare.loot.ModLootModifiers;
import com.dragoncare.sound.ModSounds;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(DragonCare.MOD_ID)
public class DragonCare {
    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    public static final String MOD_ID = "dragoncare";

    public DragonCare(IEventBus modEventBus, ModContainer container, Dist dist) {
        com.dragoncare.block.ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModItems.CREATIVE_TABS.register(modEventBus);
        ModEffects.EFFECTS.register(modEventBus);
        ModSounds.SOUNDS.register(modEventBus);
        ModLootModifiers.SERIALIZERS.register(modEventBus);
        ModDataComponents.COMPONENTS.register(modEventBus);
        com.dragoncare.recipe.ModRecipeConditions.CONDITIONS.register(modEventBus);

        container.registerConfig(ModConfig.Type.COMMON, AddonConfig.SPEC);

        modEventBus.addListener(net.neoforged.fml.event.config.ModConfigEvent.Loading.class, event -> {
            if (event.getConfig().getSpec() == AddonConfig.SPEC) {
                syncConfigs();
            }
        });
        modEventBus.addListener(net.neoforged.fml.event.config.ModConfigEvent.Reloading.class, event -> {
            if (event.getConfig().getSpec() == AddonConfig.SPEC) {
                syncConfigs();
            }
        });

        if (dist == Dist.CLIENT) {
            com.dragoncare.client.AddonClient.registerConfigScreen(container);
        }
    }

    private static boolean lastSimplifyCrafts = false;

    private static void syncConfigs() {
        if (AddonConfig.PREVENT_DRAGON_FIGHT_ALL.get() && AddonConfig.PREVENT_DRAGON_FIGHT_BABIES.get()) {
            AddonConfig.PREVENT_DRAGON_FIGHT_BABIES.set(false);
            AddonConfig.SPEC.save();
        }

        boolean currentSimplifyCrafts = AddonConfig.SIMPLIFY_CRAFTS.get();
        if (currentSimplifyCrafts != lastSimplifyCrafts) {
            lastSimplifyCrafts = currentSimplifyCrafts;
            net.minecraft.server.MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                server.execute(() -> server.reloadResources(server.getDataPackManager().getEnabledIds()));
            }
        }
    }
}
