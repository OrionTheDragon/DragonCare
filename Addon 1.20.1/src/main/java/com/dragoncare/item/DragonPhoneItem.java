package com.dragoncare.item;

import com.iafenvoy.iceandfire.entity.EntityDragonBase;
import com.dragoncare.dragonphone.PhoneServerHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
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
 * Р”СЂР°РєРѕРЅРёР№ С‚РµР»РµС„РѕРЅ вЂ” РєР°СЂРјР°РЅРЅС‹Р№ В«РіР°РґР¶РµС‚В» РґР»СЏ РѕС‚СЃР»РµР¶РёРІР°РЅРёСЏ РїСЂРёСЂСѓС‡РµРЅРЅС‹С… РґСЂР°РєРѕРЅРѕРІ.
 * <ul>
 *   <li>РџРљРњ вЂ” РїРµСЂРµРєР»СЋС‡Р°РµС‚ РІРєР»/РІС‹РєР». РџСЂРё РІРєР»СЋС‡РµРЅРёРё СЃРµСЂРІРµСЂ С€Р»С‘С‚ РІР»Р°РґРµР»СЊС†Сѓ СЃРїРёСЃРѕРє РґСЂР°РєРѕРЅРѕРІ
 *       Рё РѕС‚РєСЂС‹РІР°РµС‚ {@code DragonPhoneScreen}. Р•СЃР»Рё РІ РёРЅРІРµРЅС‚Р°СЂРµ СѓР¶Рµ Р±С‹Р» РІРєР»СЋС‡С‘РЅ РґСЂСѓРіРѕР№
 *       С‚РµР»РµС„РѕРЅ вЂ” РѕРЅ Р°РІС‚РѕРјР°С‚РёС‡РµСЃРєРё РІС‹РєР»СЋС‡Р°РµС‚СЃСЏ, С‡С‚РѕР±С‹ РѕРґРЅРѕРІСЂРµРјРµРЅРЅРѕ Р±С‹Р» Р°РєС‚РёРІРµРЅ С‚РѕР»СЊРєРѕ РѕРґРёРЅ.</li>
 *   <li>Р’ РєР°Р¶РґРѕРј С‚РёРєРµ РёРЅРІРµРЅС‚Р°СЂСЏ (РµСЃР»Рё РІРєР»СЋС‡С‘РЅ): РµСЃР»Рё РІС‹Р±СЂР°РЅРЅС‹Р№ РґСЂР°РєРѕРЅ РІ СЌС‚РѕРј Р¶Рµ РёР·РјРµСЂРµРЅРёРё
 *       Рё Р±Р»РёР¶Рµ {@value PhoneServerHelper#AUTO_OFF_DISTANCE} Рј вЂ” С‚РµР»РµС„РѕРЅ Р°РІС‚РѕРІС‹РєР»СЋС‡Р°РµС‚СЃСЏ.</li>
 *   <li>РџРѕРґСЃРІРµС‚РєСѓ (personal glow) СЂР°СЃСЃС‹Р»Р°РµС‚ {@link com.dragoncare.dragonphone.PhoneGlowTickHandler}
 *       РїРѕ С‚РёРєР°Рј РёРіСЂРѕРєР° вЂ” РіР°СЂР°РЅС‚РёСЂСѓРµС‚ СЂРѕРІРЅРѕ РѕРґРёРЅ СЃРІРµС‚СЏС‰РёР№СЃСЏ РґСЂР°РєРѕРЅ РЅР° РёРіСЂРѕРєР° Рё Р°РєРєСѓСЂР°С‚РЅРѕ
 *       РіР°СЃРёС‚ РїСЂРµРґС‹РґСѓС‰РµРіРѕ РїСЂРё СЃРјРµРЅРµ РІС‹Р±РѕСЂР°/РІС‹РєР»СЋС‡РµРЅРёРё/РїРµСЂРµРјРµС‰РµРЅРёРё С‚РµР»РµС„РѕРЅР° РёР· РёРЅРІРµРЅС‚Р°СЂСЏ.</li>
 *   <li>РџСЂРё РІС‹РїР°РґРµРЅРёРё/РїРµСЂРµРјРµС‰РµРЅРёРё РІ СЃСѓРЅРґСѓРє С‚РµР»РµС„РѕРЅ СЃР°Рј РіР°СЃРЅРµС‚ вЂ” С‡РµСЂРµР·
 *       {@code onItemEntityDestroyed}/{@code inventoryTick} РІРЅРµ РёРіСЂРѕРєР°.</li>
 * </ul>
 */
public class DragonPhoneItem extends Item {

    public DragonPhoneItem(Item.Settings settings) {
        super(settings);
    }

    public static boolean isOn(ItemStack stack) {
        if (!stack.hasNbt()) return false;
        return stack.getNbt().getBoolean("phone_on");
    }

    public static void setOn(ItemStack stack, boolean on) {
        if (on) {
            stack.getOrCreateNbt().putBoolean("phone_on", true);
        } else if (stack.hasNbt()) {
            stack.getNbt().remove("phone_on");
            stack.getNbt().remove("phone_tracked");
        }
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) {
            // РљР»РёРµРЅС‚ С‚РѕР»СЊРєРѕ РїСЂРѕРёРіСЂС‹РІР°РµС‚ В«clickВ» Рё Р¶РґС‘С‚ РїР°РєРµС‚Р° СЃ СЃРµСЂРІРµСЂР°, РєРѕС‚РѕСЂС‹Р№ РѕС‚РєСЂРѕРµС‚ UI.
            return TypedActionResult.success(stack, true);
        }
        boolean on = isOn(stack);
        if (on) {
            // Р’С‹РєР»СЋС‡Р°РµРј. РЎРЅСЏС‚РёРµ glow РІРѕР·СЊРјС‘С‚ РЅР° СЃРµР±СЏ PhoneGlowTickHandler РЅР° Р±Р»РёР¶Р°Р№С€РµРј С‚РёРєРµ.
            setOn(stack, false);
            user.playSound(SoundEvents.BLOCK_LEVER_CLICK, 0.4F, 0.7F);
        } else {
            ServerPlayerEntity sp = (ServerPlayerEntity) user;
            // Р•РґРёРЅРѕРІСЂРµРјРµРЅРЅРѕ Р°РєС‚РёРІРµРЅ С‚РѕР»СЊРєРѕ РѕРґРёРЅ С‚РµР»РµС„РѕРЅ Сѓ РёРіСЂРѕРєР° вЂ” РіР°СЃРёРј РѕСЃС‚Р°Р»СЊРЅС‹Рµ.
            deactivateOtherPhones(sp, stack);
            setOn(stack, true);
            user.playSound(SoundEvents.BLOCK_LEVER_CLICK, 0.5F, 1.4F);
            // РЁР»С‘Рј СЃРїРёСЃРѕРє Рё РїСЂРѕСЃРёРј РєР»РёРµРЅС‚ РѕС‚РєСЂС‹С‚СЊ UI.
            UUID tracked = null;
            if (stack.hasNbt() && stack.getNbt().contains("phone_tracked")) {
                tracked = stack.getNbt().getUuid("phone_tracked");
            }
            PhoneServerHelper.sendList(sp, true, tracked);
        }
        return TypedActionResult.success(stack, false);
    }

    /** Р’С‹РєР»СЋС‡Р°РµС‚ РІСЃРµ РѕСЃС‚Р°Р»СЊРЅС‹Рµ С‚РµР»РµС„РѕРЅС‹ РІ РёРЅРІРµРЅС‚Р°СЂРµ РёРіСЂРѕРєР°, РѕСЃС‚Р°РІР»СЏСЏ С‚РѕР»СЊРєРѕ {@code except}. */
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
     * РўРёРє РїСЂРµРґРјРµС‚Р° РІ РёРЅРІРµРЅС‚Р°СЂРµ. РЎРµСЂРІРµСЂ: СЃР»РµРґРёС‚ Р·Р° СѓСЃР»РѕРІРёСЏРјРё Р°РІС‚РѕРѕС‚РєР»СЋС‡РµРЅРёСЏ.
     * РџРѕРґСЃРІРµС‚РєСѓ СЂР°СЃСЃС‹Р»Р°РµС‚ РѕС‚РґРµР»СЊРЅС‹Р№ {@code PhoneGlowTickHandler}.
     */
    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (world.isClient) return;
        if (!(entity instanceof ServerPlayerEntity sp)) {
            // РўРµР»РµС„РѕРЅ РЅРµ Сѓ РёРіСЂРѕРєР° (РЅР°РїСЂРёРјРµСЂ, РІ СЃСѓРЅРґСѓРєРµ) вЂ” РіР°СЃРёРј, С‡С‚РѕР±С‹ РЅРµ В«Р·Р°РІРёСЃР°Р»В» РІРєР»СЋС‡С‘РЅРЅС‹Рј.
            if (isOn(stack)) setOn(stack, false);
            return;
        }
        if (!isOn(stack)) return;

        UUID tracked = null;
        if (stack.hasNbt() && stack.getNbt().contains("phone_tracked")) {
            tracked = stack.getNbt().getUuid("phone_tracked");
        }
        if (tracked == null) return;

        MinecraftServer server = sp.getServer();
        if (server == null) return;
        EntityDragonBase dragon = PhoneServerHelper.findLive(server, tracked);
        if (dragon == null) return; // РґСЂР°РєРѕРЅ РЅРµ Р·Р°РіСЂСѓР¶РµРЅ вЂ” РЅРµС‡РµРіРѕ РїСЂРѕРІРµСЂСЏС‚СЊ РІ СЌС‚РѕС‚ С‚РёРє

        // РђРІС‚Рѕ-РІС‹РєР»: С‚РѕС‚ Р¶Рµ РјРёСЂ + СЂР°СЃСЃС‚РѕСЏРЅРёРµ < 10Рј.
        if (dragon.getWorld() == sp.getWorld()) {
            double dist = sp.getPos().distanceTo(dragon.getPos());
            if (dist < PhoneServerHelper.AUTO_OFF_DISTANCE) {
                setOn(stack, false);
                sp.playSound(SoundEvents.BLOCK_LEVER_CLICK, 0.4F, 0.7F);
            }
        }
    }

    /** РљРѕРіРґР° РІС‹РєРёРЅСѓС‚С‹Р№ С‚РµР»РµС„РѕРЅ-СЃСѓС‰РЅРѕСЃС‚СЊ СѓРЅРёС‡С‚РѕР¶Р°РµС‚СЃСЏ/СЃРѕР±РёСЂР°РµС‚СЃСЏ вЂ” СЃРѕСЃС‚РѕСЏРЅРёРµ В«РІРєР»В» СѓР¶Рµ РЅРµ РёРјРµРµС‚ СЃРјС‹СЃР»Р°. */
    @Override
    public void onItemEntityDestroyed(net.minecraft.entity.ItemEntity entity) {
        ItemStack s = entity.getStack();
        if (isOn(s)) setOn(s, false);
    }

    @Override
    public void appendTooltip(ItemStack stack, @org.jetbrains.annotations.Nullable World world, List<Text> tooltip, net.minecraft.client.item.TooltipContext context) {
        // In 1.20.1 Yarn TooltipContext is different, so I'll just suppress any compile issues by matching 1.20.1 signature
        // Actually, the interface is net.minecraft.client.item.TooltipContext in 1.20.1 Yarn
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



