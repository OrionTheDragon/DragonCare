package com.dragoncare.item;

import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import com.dragoncare.dragonphone.ModDataComponents;
import com.dragoncare.dragonphone.PhoneServerHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;
import java.util.UUID;

/**
 * Драконий телефон — карманный «гаджет» для отслеживания прирученных драконов.
 * <ul>
 *   <li>ПКМ — переключает вкл/выкл. При включении сервер шлёт владельцу список драконов
 *       и открывает {@code DragonPhoneScreen}. Если в инвентаре уже был включён другой
 *       телефон — он автоматически выключается, чтобы одновременно был активен только один.</li>
 *   <li>В каждом тике инвентаря (если включён): если выбранный дракон в этом же измерении
 *       и ближе {@value PhoneServerHelper#AUTO_OFF_DISTANCE} м — телефон автовыключается.</li>
 *   <li>Подсветку (personal glow) рассылает {@link com.dragoncare.dragonphone.PhoneGlowTickHandler}
 *       по тикам игрока — гарантирует ровно один светящийся дракон на игрока и аккуратно
 *       гасит предыдущего при смене выбора/выключении/перемещении телефона из инвентаря.</li>
 *   <li>При выпадении/перемещении в сундук телефон сам гаснет — через
 *       {@code onItemEntityDestroyed}/{@code inventoryTick} вне игрока.</li>
 * </ul>
 */
public class DragonPhoneItem extends Item {

    public DragonPhoneItem(Item.Settings settings) {
        super(settings);
    }

    public static boolean isOn(ItemStack stack) {
        Boolean v = stack.get(ModDataComponents.PHONE_ON.get());
        return v != null && v;
    }

    public static void setOn(ItemStack stack, boolean on) {
        if (on) stack.set(ModDataComponents.PHONE_ON.get(), Boolean.TRUE);
        else {
            stack.remove(ModDataComponents.PHONE_ON.get());
            stack.remove(ModDataComponents.PHONE_TRACKED.get());
        }
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) {
            // Клиент только проигрывает «click» и ждёт пакета с сервера, который откроет UI.
            return TypedActionResult.success(stack, true);
        }
        boolean on = isOn(stack);
        if (on) {
            // Выключаем. Снятие glow возьмёт на себя PhoneGlowTickHandler на ближайшем тике.
            setOn(stack, false);
            user.playSound(SoundEvents.BLOCK_LEVER_CLICK, 0.4F, 0.7F);
        } else {
            ServerPlayerEntity sp = (ServerPlayerEntity) user;
            // Единовременно активен только один телефон у игрока — гасим остальные.
            deactivateOtherPhones(sp, stack);
            setOn(stack, true);
            user.playSound(SoundEvents.BLOCK_LEVER_CLICK, 0.5F, 1.4F);
            // Шлём список и просим клиент открыть UI.
            PhoneServerHelper.sendList(sp, true, stack.get(ModDataComponents.PHONE_TRACKED.get()));
        }
        return TypedActionResult.success(stack, false);
    }

    /** Выключает все остальные телефоны в инвентаре игрока, оставляя только {@code except}. */
    private static void deactivateOtherPhones(ServerPlayerEntity sp, ItemStack except) {
        var inv = sp.getInventory();
        deactivateList(inv.main, except);
        deactivateList(inv.offHand, except);
        deactivateList(inv.armor, except);
    }

    private static void deactivateList(List<ItemStack> list, ItemStack except) {
        for (ItemStack s : list) {
            if (s == except || s.isEmpty()) continue;
            if (!(s.getItem() instanceof DragonPhoneItem)) continue;
            if (isOn(s)) setOn(s, false);
        }
    }

    /**
     * Тик предмета в инвентаре. Сервер: следит за условиями автоотключения.
     * Подсветку рассылает отдельный {@code PhoneGlowTickHandler}.
     */
    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (world.isClient) return;
        if (!(entity instanceof ServerPlayerEntity sp)) {
            // Телефон не у игрока (например, в сундуке) — гасим, чтобы не «зависал» включённым.
            if (isOn(stack)) setOn(stack, false);
            return;
        }
        if (!isOn(stack)) return;

        UUID tracked = stack.get(ModDataComponents.PHONE_TRACKED.get());
        if (tracked == null) return;

        MinecraftServer server = sp.getServer();
        if (server == null) return;
        DragonBaseEntity dragon = PhoneServerHelper.findLive(server, tracked);
        if (dragon == null) return; // дракон не загружен — нечего проверять в этот тик

        // Авто-выкл: тот же мир + расстояние < 10м.
        if (dragon.getWorld() == sp.getWorld()) {
            double dist = sp.getPos().distanceTo(dragon.getPos());
            if (dist < PhoneServerHelper.AUTO_OFF_DISTANCE) {
                setOn(stack, false);
                sp.playSound(SoundEvents.BLOCK_LEVER_CLICK, 0.4F, 0.7F);
            }
        }
    }

    /** Когда выкинутый телефон-сущность уничтожается/собирается — состояние «вкл» уже не имеет смысла. */
    @SuppressWarnings("deprecation")
    @Override
    public void onItemEntityDestroyed(net.minecraft.entity.ItemEntity entity) {
        ItemStack s = entity.getStack();
        if (isOn(s)) setOn(s, false);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        if (isOn(stack)) {
            tooltip.add(Text.translatable("item.dragoncare.dragon_phone.tip_on").formatted(Formatting.GREEN));
        } else {
            tooltip.add(Text.translatable("item.dragoncare.dragon_phone.tip_off").formatted(Formatting.GRAY));
        }
        tooltip.add(Text.translatable("item.dragoncare.dragon_phone.tip_hint").formatted(Formatting.DARK_GRAY));
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        // Suppress the bobbing animation if the item is still a Dragon Phone and only its components changed
        if (!slotChanged && oldStack.isOf(this) && newStack.isOf(this)) {
            return false;
        }
        return super.shouldCauseReequipAnimation(oldStack, newStack, slotChanged);
    }
}
