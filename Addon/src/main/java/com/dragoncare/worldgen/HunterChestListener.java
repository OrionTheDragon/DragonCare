package com.dragoncare.worldgen;

import com.dragoncare.DragonCare;
import com.dragoncare.item.ModItems;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;

/**
 * Watches container-open events and grants the "Echo of Cruelty" advancement
 * when a player opens a dragon-hunter chest whose contents include a syringe
 * or scale shears.
 *
 * <p>Hunter chest positions are tracked in {@link DragonHunterState}. A simple
 * Chebyshev-radius lookup (≤6 blocks) decides whether the just-opened container
 * is one of ours — this works for vanilla chests and is also resilient to
 * Lootr's per-player chest replacement, because Lootr keeps the original
 * coordinates.</p>
 */
@EventBusSubscriber(modid = DragonCare.MOD_ID)
public final class HunterChestListener {

    public static final Identifier ECHO_OF_CRUELTY =
            Identifier.of(DragonCare.MOD_ID, "echo_of_cruelty");

    private static final int REACH = 6;

    private HunterChestListener() {}

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayerEntity player)) return;
        if (!(player.getWorld() instanceof ServerWorld world)) return;

        DragonHunterState state = DragonHunterState.get(world);
        if (state.hunterChestPositions().isEmpty()) return;

        BlockPos chestPos = findNearbyHunterChest(player.getBlockPos(), state);
        if (chestPos == null) return;

        ScreenHandler handler = event.getContainer();
        if (handler == null) return;

        // Only inspect non-player slots. Lootr/vanilla loads loot when slots
        // are first read for display, so by the time Open fires items are present.
        for (Slot slot : handler.slots) {
            if (slot.inventory == player.getInventory()) continue;
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;
            if (stack.isOf(ModItems.DRAGON_BLOOD_SYRINGE.get())
                    || stack.isOf(ModItems.SCALE_SHEARS.get())) {
                grant(player);
                return;
            }
        }
    }

    private static BlockPos findNearbyHunterChest(BlockPos playerPos, DragonHunterState state) {
        for (Long packed : state.hunterChestPositions()) {
            BlockPos pos = BlockPos.fromLong(packed);
            if (Math.abs(pos.getX() - playerPos.getX()) <= REACH
                    && Math.abs(pos.getY() - playerPos.getY()) <= REACH
                    && Math.abs(pos.getZ() - playerPos.getZ()) <= REACH) {
                return pos;
            }
        }
        return null;
    }

    private static void grant(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        AdvancementEntry adv = server.getAdvancementLoader().get(ECHO_OF_CRUELTY);
        if (adv == null) return;
        PlayerAdvancementTracker tracker = player.getAdvancementTracker();
        AdvancementProgress progress = tracker.getProgress(adv);
        if (progress.isDone()) return;
        for (String name : progress.getUnobtainedCriteria()) {
            tracker.grantCriterion(adv, name);
        }
    }
}
