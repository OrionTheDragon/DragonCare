package com.dragoncare.advancement;

import com.dragoncare.DragonCare;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;

@EventBusSubscriber(modid = DragonCare.MOD_ID)
public class HunterDiaryTracker {

    private static int totalDiariesCount = -1;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayerEntity player)) return;
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
            ItemStack stack = player.getInventory().getStackInSlot(i);
            if (stack.isOf(Items.WRITTEN_BOOK)) {
                WrittenBookContentComponent content = stack.get(DataComponentTypes.WRITTEN_BOOK_CONTENT);
                if (content != null && content.title().raw().startsWith("Дневник охотника")) {
                    if (state.addDiary(player.getUuid(), content.title().raw())) {
                        newlyFound = true;
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
