package com.dragoncare.item;

import com.dragoncare.config.AddonConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AshSensorItem extends Item {
    
    private static Set<Block> ASH_BLOCKS = null;

    public AshSensorItem(Settings settings) {
        super(settings);
    }

    private static void initAshBlocks() {
        if (ASH_BLOCKS != null) return;
        ASH_BLOCKS = new HashSet<>();
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

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        
        if (world.isClient) {
            return TypedActionResult.success(stack);
        }

        boolean isManual = stack.hasNbt() && stack.getNbt().getBoolean("sensor_manual");
        
        if (user.isSneaking()) {
            boolean newMode = !isManual;
            stack.getOrCreateNbt().putBoolean("sensor_manual", newMode);
            
            if (newMode) {
                stack.getOrCreateNbt().putBoolean("sensor_on", true);
                user.sendMessage(Text.translatable("message.dragoncare.ash_sensor.mode_manual").formatted(Formatting.YELLOW), true);
            } else {
                stack.getOrCreateNbt().putBoolean("sensor_on", true);
                user.sendMessage(Text.translatable("message.dragoncare.ash_sensor.mode_auto").formatted(Formatting.YELLOW), true);
            }
            world.playSound(null, user.getBlockPos(), SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.PLAYERS, 0.4f, 1.2f);
        } else {
            if (isManual) {
                performScan(world, user, stack);
                stack.getOrCreateNbt().putBoolean("sensor_on", true);
                world.playSound(null, user.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.PLAYERS, 0.8f, 1.5f);
            } else {
                boolean isOn = stack.hasNbt() && stack.getNbt().getBoolean("sensor_on");
                stack.getOrCreateNbt().putBoolean("sensor_on", !isOn);
                if (!isOn) {
                    performScan(world, user, stack);
                    world.playSound(null, user.getBlockPos(), SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.PLAYERS, 0.5f, 1.5f);
                } else {
                    world.playSound(null, user.getBlockPos(), SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.PLAYERS, 0.5f, 0.8f);
                }
            }
        }
        
        return TypedActionResult.success(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (world.isClient || !(entity instanceof PlayerEntity player)) return;

        boolean isHeld = selected || player.getOffHandStack() == stack;
        boolean isOn = stack.hasNbt() && stack.getNbt().getBoolean("sensor_on");
        boolean isManual = stack.hasNbt() && stack.getNbt().getBoolean("sensor_manual");

        if (!com.dragoncare.config.AddonConfig.ASH_POISONING_ENABLED.get()) {
            if (isOn) {
                stack.getOrCreateNbt().putBoolean("sensor_on", false);
                stack.getOrCreateNbt().putFloat("sensor_level", 0.0f);
            }
            return;
        }

        if (!isHeld) {
            if (isOn) {
                stack.getOrCreateNbt().putBoolean("sensor_on", false);
            }
            return;
        }

        if (isOn && !isManual) {
            if ((world.getTime() + entity.getId()) % 30 == 0) {
                performScan(world, entity, stack);
            }
        }
    }

    private void performScan(World world, Entity entity, ItemStack stack) {
        initAshBlocks();
        if (ASH_BLOCKS.isEmpty()) {
            stack.getOrCreateNbt().putFloat("sensor_level", 0.0f);
            return;
        }

        int maxStage4 = AddonConfig.ASH_STAGE_4.get();
        if (maxStage4 <= 0) maxStage4 = 85;

        BlockPos center = entity.getBlockPos();
        int radius = 15;
        
        int minX = center.getX() - radius;
        int maxX = center.getX() + radius;
        int minY = center.getY() - radius;
        int maxY = center.getY() + radius;
        int minZ = center.getZ() - radius;
        int maxZ = center.getZ() + radius;

        int ashCount = 0;
        BlockPos.Mutable pos = new BlockPos.Mutable();
        
        outer:
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                pos.set(x, 0, z);
                if (!world.isChunkLoaded(pos)) {
                    z = (z | 15);
                    continue;
                }
                
                net.minecraft.world.chunk.Chunk chunk = world.getChunk(pos);
                
                for (int y = minY; y <= maxY; y++) {
                    pos.set(x, y, z);
                    BlockState state = chunk.getBlockState(pos);
                    if (ASH_BLOCKS.contains(state.getBlock())) {
                        ashCount++;
                        if (ashCount >= maxStage4) {
                            break outer;
                        }
                    }
                }
            }
        }

        float level = Math.min(1.0f, (float) ashCount / (float) maxStage4);
        stack.getOrCreateNbt().putFloat("sensor_level", level);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        if (!slotChanged && oldStack.getItem() == this && newStack.getItem() == this) {
            return false;
        }
        return super.shouldCauseReequipAnimation(oldStack, newStack, slotChanged);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("item.dragoncare.ash_sensor.tooltip").formatted(Formatting.GRAY));
        
        boolean isManual = stack.hasNbt() && stack.getNbt().getBoolean("sensor_manual");
        if (isManual) {
            tooltip.add(Text.translatable("item.dragoncare.ash_sensor.tip_manual").formatted(Formatting.YELLOW));
        } else {
            tooltip.add(Text.translatable("item.dragoncare.ash_sensor.tip_auto").formatted(Formatting.GREEN));
        }
        tooltip.add(Text.translatable("item.dragoncare.ash_sensor.tip_hint").formatted(Formatting.DARK_GRAY));
    }
}


