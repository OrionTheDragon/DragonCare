package com.dragoncare.advancement;

import com.dragoncare.DragonCare;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;

import java.util.Map;

@EventBusSubscriber(modid = DragonCare.MOD_ID)
public class HunterDiaryTracker {

    private static int totalDiariesCount = -1;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayerEntity player)) return;
        if (player.age % 20 != 0) return; // Check once per second

        MinecraftServer server = player.getServer();
        if (server == null) return;

        // Retroactively check and grant Three Elements gate if core elements are met
        AchievementGranter.checkAndUnlockThreeElements(player);

        // Lazy init total count
        if (totalDiariesCount == -1) {
            totalDiariesCount = calculateTotalDiaries(server);
        }

        DiaryState state = DiaryState.get(server);
        boolean newlyFound = false;

        // Scan inventory for written books
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(Items.WRITTEN_BOOK)) {
                net.minecraft.nbt.NbtCompound nbt = stack.getNbt();
                if (nbt != null && nbt.contains("title", net.minecraft.nbt.NbtElement.STRING_TYPE)) {
                    String title = nbt.getString("title");
                    if (title.startsWith("Дневник охотника")) {
                        if (state.addDiary(player.getUuid(), title)) {
                            newlyFound = true;
                        }
                    }
                }
            }
        }

        if (newlyFound && totalDiariesCount > 0 && state.getCount(player.getUuid()) >= totalDiariesCount) {
            AchievementGranter.grant(player, AchievementGranter.ALL_HUNTER_DIARIES);
        }
    }

    private static int calculateTotalDiaries(MinecraftServer server) {
        try {
            // Count all loot tables that define hunter diaries
            Map<Identifier, ?> resources = server.getResourceManager().findResources("loot_table/chest/dragon_hunter", 
                path -> path.getPath().startsWith("loot_table/chest/dragon_hunter/book_") && path.getPath().endsWith(".json"));
            return resources.size();
        } catch (Exception e) {
            return 3; // Fallback to 3 if something goes wrong
        }
    }
}


