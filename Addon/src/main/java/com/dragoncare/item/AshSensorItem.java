package com.dragoncare.item;

import com.dragoncare.config.AddonConfig;
import com.dragoncare.dragonphone.ModDataComponents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
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
        // NOTE: Ice and Fire CE registers the charred blocks with a single 'r'
        // ("chared_*") — upstream typo. Must match AshPoisoningSystem's list
        // exactly, otherwise the sensor resolves these ids to AIR and silently
        // detects only plain "ash".
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

        boolean isManual = Boolean.TRUE.equals(stack.get(ModDataComponents.SENSOR_MANUAL.get()));
        
        if (user.isSneaking()) {
            // Toggle Mode
            boolean newMode = !isManual;
            stack.set(ModDataComponents.SENSOR_MANUAL.get(), newMode);
            
            if (newMode) {
                // Switching to manual
                stack.set(ModDataComponents.SENSOR_ON.get(), true);
                user.sendMessage(Text.translatable("message.dragoncare.ash_sensor.mode_manual").formatted(Formatting.YELLOW), true);
            } else {
                // Switching to automatic (continuous). Stay ON and start scanning.
                stack.set(ModDataComponents.SENSOR_ON.get(), true);
                user.sendMessage(Text.translatable("message.dragoncare.ash_sensor.mode_auto").formatted(Formatting.YELLOW), true);
            }
            world.playSound(null, user.getBlockPos(), SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.PLAYERS, 0.4f, 1.2f);
        } else {
            // Action based on mode
            if (isManual) {
                // Perform one scan
                performScan(world, user, stack);
                stack.set(ModDataComponents.SENSOR_ON.get(), true); // Ensure it's on to show the result
                world.playSound(null, user.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.PLAYERS, 0.8f, 1.5f);
            } else {
                // Toggle ON/OFF
                boolean isOn = Boolean.TRUE.equals(stack.get(ModDataComponents.SENSOR_ON.get()));
                stack.set(ModDataComponents.SENSOR_ON.get(), !isOn);
                if (!isOn) {
                    // Just turned on, do immediate scan
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
        boolean isOn = Boolean.TRUE.equals(stack.get(ModDataComponents.SENSOR_ON.get()));
        boolean isManual = Boolean.TRUE.equals(stack.get(ModDataComponents.SENSOR_MANUAL.get()));

        // Master kill-switch: if ash poisoning is disabled the sensor mechanic has
        // nothing to detect for, so skip every per-tick check entirely. We also
        // make sure the stored "on" flag is cleared, so re-enabling the feature
        // doesn't leave a stale activation that quietly survived the disable.
        if (!com.dragoncare.config.AddonConfig.ASH_POISONING_ENABLED.get()) {
            if (isOn) {
                stack.set(ModDataComponents.SENSOR_ON.get(), false);
                stack.set(ModDataComponents.SENSOR_LEVEL.get(), 0.0f);
            }
            return;
        }

        if (!isHeld) {
            if (isOn) {
                stack.set(ModDataComponents.SENSOR_ON.get(), false);
            }
            return;
        }

        if (isOn && !isManual) {
            // Оптимизация 1: распределение нагрузки. Привязываем тик сканирования к ID сущности,
            // чтобы сканирования разных игроков не происходили в один и тот же тик сервера, вызывая лаг.
            if ((world.getTime() + entity.getId()) % 30 == 0) {
                performScan(world, entity, stack);
            }
        }
    }

    private void performScan(World world, Entity entity, ItemStack stack) {
        initAshBlocks();
        if (ASH_BLOCKS.isEmpty()) {
            stack.set(ModDataComponents.SENSOR_LEVEL.get(), 0.0f);
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
                
                // Оптимизация 2: получаем чанк один раз на весь столбец Y
                // Это позволяет избежать медленного метода world.getBlockState, 
                // который делает поиск чанка каждый раз в цикле
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
        stack.set(ModDataComponents.SENSOR_LEVEL.get(), level);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        // Предотвращаем "моргание" (анимацию переэкипировки) датчика, 
        // когда его NBT (Data Components) обновляются на лету.
        if (!slotChanged && oldStack.getItem() == this && newStack.getItem() == this) {
            return false;
        }
        return super.shouldCauseReequipAnimation(oldStack, newStack, slotChanged);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.dragoncare.ash_sensor.tooltip").formatted(Formatting.GRAY));
        
        boolean isManual = Boolean.TRUE.equals(stack.get(ModDataComponents.SENSOR_MANUAL.get()));
        if (isManual) {
            tooltip.add(Text.translatable("item.dragoncare.ash_sensor.tip_manual").formatted(Formatting.YELLOW));
        } else {
            tooltip.add(Text.translatable("item.dragoncare.ash_sensor.tip_auto").formatted(Formatting.GREEN));
        }
        tooltip.add(Text.translatable("item.dragoncare.ash_sensor.tip_hint").formatted(Formatting.DARK_GRAY));
    }
}
