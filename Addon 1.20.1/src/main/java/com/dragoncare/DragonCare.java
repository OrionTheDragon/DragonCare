package com.dragoncare;

import com.dragoncare.config.AddonConfig;
import com.dragoncare.effect.ModEffects;
import com.dragoncare.item.ModItems;
import com.dragoncare.loot.ModLootModifiers;
import com.dragoncare.sound.ModSounds;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(DragonCare.MOD_ID)
public class DragonCare {
    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    public static final String MOD_ID = "dragoncare";

    public DragonCare() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        com.dragoncare.block.ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModItems.CREATIVE_TABS.register(modEventBus);
        ModEffects.EFFECTS.register(modEventBus);
        ModSounds.SOUNDS.register(modEventBus);
        ModLootModifiers.SERIALIZERS.register(modEventBus);

        // Move registration to RegisterEvent
        modEventBus.addListener((net.minecraftforge.registries.RegisterEvent event) -> {
            if (event.getRegistryKey().equals(net.minecraftforge.registries.ForgeRegistries.Keys.RECIPE_SERIALIZERS)) {
                net.minecraftforge.common.crafting.CraftingHelper.register(com.dragoncare.recipe.SimplifyCraftsCondition.Serializer.INSTANCE);
            }
        });

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, AddonConfig.SPEC);

        modEventBus.addListener((net.minecraftforge.fml.event.config.ModConfigEvent.Loading event) -> {
            if (event.getConfig().getSpec() == AddonConfig.SPEC) {
                syncConfigs();
            }
        });
        modEventBus.addListener((net.minecraftforge.fml.event.config.ModConfigEvent.Reloading event) -> {
            if (event.getConfig().getSpec() == AddonConfig.SPEC) {
                syncConfigs();
            }
        });
        modEventBus.addListener(this::setup);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.dragoncare.client.AddonClient.register(modEventBus);
        }
    }

    private void setup(final net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            com.dragoncare.network.ModNetwork.register();
        });
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
            net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                server.execute(() -> {
                    server.reloadResources(server.getDataPackManager().getEnabledNames()).thenAccept(v -> {
                        server.getPlayerManager().onDataPacksReloaded();
                    });
                });
            }
        }
    }
}


