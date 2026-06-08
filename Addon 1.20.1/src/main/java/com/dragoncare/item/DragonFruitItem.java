package com.dragoncare.item;

import com.iafenvoy.iceandfire.entity.EntityDragonBase;
import com.dragoncare.taming.BondManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.List;

/**
 * Р”СЂР°РєРѕРЅРёР№ С„СЂСѓРєС‚.
 * <ul>
 *   <li>РРіСЂРѕРє РјРѕР¶РµС‚ СЃСЉРµСЃС‚СЊ РєР°Рє РѕР±С‹С‡РЅСѓСЋ РµРґСѓ (6 РіРѕР»РѕРґР°, 3 СЃС‹С‚РѕСЃС‚Рё вЂ” Р·Р°РґР°С‘С‚СЃСЏ РІ {@link ModItems}
 *       С‡РµСЂРµР· {@code FoodComponent}).</li>
 *   <li>РџСЂРёРјРµРЅСЏСЏ РЅР° СЃРѕР±СЃС‚РІРµРЅРЅРѕРј РїСЂРёСЂСѓС‡РµРЅРЅРѕРј РґСЂР°РєРѕРЅРµ: РґР°С‘С‚ +6 Рє РїСЂРёРІСЏР·Р°РЅРЅРѕСЃС‚Рё
 *       (РІ РѕР±С…РѕРґ 10-РјРёРЅСѓС‚РЅРѕРіРѕ РєСѓР»РґР°СѓРЅР° {@link BondManager#FEED_CAP_USES}),
 *       СЂР°СЃС‚РёС‚ РґСЂР°РєРѕРЅР° РЅР° 1 РґРµРЅСЊ Рё РїРѕРїРѕР»РЅСЏРµС‚ РµРјСѓ РіРѕР»РѕРґ (РєР°Рє РІР°РЅРёР»СЊРЅС‹Р№
 *       {@code iceandfire:dragon_meal}).</li>
 * </ul>
 */
public class DragonFruitItem extends BlockItem {

    /** РЎРєРѕР»СЊРєРѕ РѕС‡РєРѕРІ РїСЂРёРІСЏР·Р°РЅРЅРѕСЃС‚Рё РґР°С‘С‚ РѕРґРЅРѕ СЃРєР°СЂРјР»РёРІР°РЅРёРµ С„СЂСѓРєС‚Р°. */
    public static final int BOND_PER_FEED = 6;
    /** РќР° СЃРєРѕР»СЊРєРѕ СЂР°СЃС‚С‘С‚ РґСЂР°РєРѕРЅ (РІ РёРіСЂРѕРІС‹С… РґРЅСЏС…) РїСЂРё СЃРєР°СЂРјР»РёРІР°РЅРёРё. */
    private static final int GROWTH_DAYS = 1;
    /** РЎРєРѕР»СЊРєРѕ РµРґРёРЅРёС† РіРѕР»РѕРґР° РґР°С‘С‚ С„СЂСѓРєС‚ РґСЂР°РєРѕРЅСѓ РїСЂРё СЃРєР°СЂРјР»РёРІР°РЅРёРё. */
    private static final int HUNGER_GAIN = 20;
    /** РҐР°СЂРґ-РєР°Рї РіРѕР»РѕРґР° РІ CE. */
    private static final int HUNGER_CAP = 100;

    public DragonFruitItem(Block block, Settings settings) {
        super(block, settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.world.World world, java.util.List<net.minecraft.text.Text> tooltip, net.minecraft.client.item.TooltipContext context) {
        super.appendTooltip(stack, world, tooltip, context);
        tooltip.add(Text.translatable("item.dragoncare.dragon_fruit.desc_0").formatted(Formatting.GRAY));
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (!(entity instanceof EntityDragonBase dragon)) {
            return ActionResult.PASS;
        }
        if (dragon.isModelDead()) {
            return ActionResult.PASS;
        }
        // РљРѕСЂРјРёС‚СЊ РјРѕР¶РЅРѕ С‚РѕР»СЊРєРѕ СЃРІРѕРµРіРѕ РїСЂРёСЂСѓС‡РµРЅРЅРѕРіРѕ РґСЂР°РєРѕРЅР°. РРЅР°С‡Рµ вЂ” РґР°С‘Рј fall-through РЅР° РµРґСѓ.
        if (dragon.getOwnerUuid() == null || !dragon.getOwnerUuid().equals(user.getUuid())) {
            return ActionResult.PASS;
        }
        if (user.getWorld().isClient) {
            return ActionResult.SUCCESS;
        }
        if (!(user instanceof ServerPlayerEntity serverPlayer)) {
            return ActionResult.PASS;
        }

        // РџСЂРёРІСЏР·Р°РЅРЅРѕСЃС‚СЊ вЂ” РІ РѕР±С…РѕРґ 10-РјРёРЅСѓС‚РЅРѕРіРѕ РєСѓР»РґР°СѓРЅР°
        BondManager.onFeedBypass(serverPlayer, dragon, BOND_PER_FEED);

        // Р РѕСЃС‚ + РµРґР° (РєР°Рє iceandfire:dragon_meal РІ CE)
        try {
            dragon.growDragon(GROWTH_DAYS);
        } catch (Throwable ignored) {
            // РќР° СЃР»СѓС‡Р°Р№ РЅРµСЃРѕРІРјРµСЃС‚РёРјРѕР№ РІРµС‚РєРё CE вЂ” РїСЂРёРѕСЂРёС‚РµС‚ Сѓ РїСЂРёРІСЏР·Р°РЅРЅРѕСЃС‚Рё
        }
        try {
            dragon.setHunger(Math.min(HUNGER_CAP, dragon.getHunger() + HUNGER_GAIN));
        } catch (Throwable ignored) {
            // СЃРј. РІС‹С€Рµ
        }

        // Р—РІСѓРє + С‡Р°СЃС‚РёС†С‹ (РєР°Рє РїСЂРё РєРѕСЂРјР»РµРЅРёРё iceandfire:dragon_meal),
        // РЅРѕ С‡Р°СЃС‚РёС†С‹ Р·Р°РјРµРЅРµРЅС‹ РЅР° РєСѓСЃРєРё СЃР°РјРѕРіРѕ С„СЂСѓРєС‚Р° вЂ” РѕРЅРё РєСЂР°СЃРЅС‹Рµ.
        playFeedFx(dragon, stack);

        if (!user.isCreative()) stack.decrement(1);
        return ActionResult.SUCCESS;
    }

    /**
     * Р’РѕСЃРїСЂРѕРёР·РІРѕРґРёС‚ РЅР° РґСЂР°РєРѕРЅР° С‚РѕС‚ Р¶Рµ Р·РІСѓРє, С‡С‚Рѕ Рё РїСЂРё РїРѕРµРґР°РЅРёРё {@code iceandfire:dragon_meal}
     * ({@link SoundEvents#ENTITY_GENERIC_EAT}), Рё СЃРїР°РІРЅРёС‚ РёР· РµРіРѕ РїР°СЃС‚Рё РѕР±Р»Р°РєРѕ С‡Р°СЃС‚РёС†
     * {@link ItemStackParticleEffect} СЃ РєСѓСЃРєРѕРј РґСЂР°РєРѕРЅСЊРµРіРѕ С„СЂСѓРєС‚Р° (РґР°С‘С‚ РєСЂР°СЃРЅС‹Р№ С†РІРµС‚
     * РїРѕРґ С†РІРµС‚ СЃР°РјРѕРіРѕ С„СЂСѓРєС‚Р°).
     *
     * <p>Р—Р° РѕСЃРЅРѕРІСѓ РІР·СЏС‚ {@code EntityDragonBase#spawnItemCrackParticles(Item)} РёР· CE
     * (С‚РѕС‡РєР° СЃРїР°РІРЅР° вЂ” {@link EntityDragonBase#getHeadPosition()}), РЅРѕ РєРѕР»РёС‡РµСЃС‚РІРѕ, СЂР°Р·Р»С‘С‚
     * Рё СЃС‚Р°СЂС‚РѕРІС‹Р№ СЂР°Р·Р±СЂРѕСЃ РјР°СЃС€С‚Р°Р±РёСЂСѓСЋС‚СЃСЏ РїРѕ {@link EntityDragonBase#getRenderSize()},
     * С‡С‚РѕР±С‹ Сѓ РєСЂСѓРїРЅРѕРіРѕ РІР·СЂРѕСЃР»РѕРіРѕ РґСЂР°РєРѕРЅР° СЌС„С„РµРєС‚ РІС‹РіР»СЏРґРµР» СЃРѕСЂР°Р·РјРµСЂРЅРѕ С‚СѓС€Рµ, Р° РЅРµ РєР°Рє
     * РїР°СЂР° Р·С‘СЂРЅС‹С€РµРє Сѓ РїР°СЃС‚Рё.</p>
     */
    private static void playFeedFx(EntityDragonBase dragon, ItemStack fruitStack) {
        World world = dragon.getWorld();

        // Р—РІСѓРє вЂ” С‚РѕС‚ Р¶Рµ, С‡С‚Рѕ РІ CE РґР»СЏ dragon_meal.
        // Entity.playSound вЂ” РїСѓР±Р»РёС‡РЅС‹Р№ РјРµС‚РѕРґ, РІ РѕС‚Р»РёС‡РёРµ РѕС‚ protected getSoundVolume/Pitch.
        dragon.playSound(SoundEvents.ENTITY_GENERIC_EAT, 1.0F, 1.0F);

        if (!(world instanceof ServerWorld serverWorld)) return;

        // ItemStackParticleEffect СЃ С„СЂСѓРєС‚РѕРј вЂ” С‚РµРєСЃС‚СѓСЂР° С‡Р°СЃС‚РёС†С‹ Р±РµСЂС‘С‚СЃСЏ РёР· СЃРїСЂР°Р№С‚Р° РїСЂРµРґРјРµС‚Р°
        // (С‚.Рµ. РєСЂР°СЃРЅР°СЏ). РќР° СЃРµСЂРІРµСЂРµ РёСЃРїРѕР»СЊР·СѓРµРј spawnParticles, С‡С‚РѕР±С‹ РїР°РєРµС‚ СѓС€С‘Р» РІСЃРµРј РєР»РёРµРЅС‚Р°Рј.
        ItemStackParticleEffect effect = new ItemStackParticleEffect(ParticleTypes.ITEM, fruitStack);

        // getHeadPosition() вЂ” РїСѓР±Р»РёС‡РЅС‹Р№ РјРµС‚РѕРґ EntityDragonBase, РІРѕР·РІСЂР°С‰Р°РµС‚ СЂРµР°Р»СЊРЅСѓСЋ С‚РѕС‡РєСѓ
        // РїР°СЃС‚Рё СЃ СѓС‡С‘С‚РѕРј РїРѕР·С‹/Р°РЅРёРјР°С†РёРё РґСЂР°РєРѕРЅР°; РёРјРµРЅРЅРѕ РµРіРѕ РґС‘СЂРіР°РµС‚ CE РґР»СЏ dragon_meal.
        Vec3d head = dragon.getHeadPosition();
        Random random = dragon.getRandom();

        // getRenderSize() РІ CE: ~3 Сѓ РЅРѕРІРѕСЂРѕР¶РґС‘РЅРЅРѕРіРѕ, РґРѕ ~20 Сѓ РІР·СЂРѕСЃР»РѕРіРѕ 5-Р№ СЃС‚Р°РґРёРё.
        // Р‘Р°Р·Сѓ 3 Р±РµСЂС‘Рј РєР°Рє В«РµРґРёРЅРёС†СѓВ» вЂ” Сѓ РјР°Р»С‹С€Р° scaleв‰€1, Сѓ РєСЂСѓРїРЅРѕРіРѕ в‰€6-7.
        float renderSize = Math.max(1.5F, dragon.getRenderSize());
        float scale = Math.max(0.6F, renderSize / 3.0F);

        // РљРѕР»-РІРѕ С‡Р°СЃС‚РёС†: РјР°Р»С‹С€ ~13, РІР·СЂРѕСЃР»С‹Р№ ~40+ (РІРёР·СѓР°Р»СЊРЅРѕ Р·Р°РјРµС‚РЅРµРµ РЅР° Р±РѕР»СЊС€РѕР№ РїР°СЃС‚Рё).
        int count = (int) (10 + scale * 5.0F);

        // РЎС‚Р°СЂС‚РѕРІС‹Р№ СЂР°Р·Р±СЂРѕСЃ РІРѕРєСЂСѓРі РїР°СЃС‚Рё Рё СЃРёРіРјР° РїРѕ СЃРєРѕСЂРѕСЃС‚Рё РјР°СЃС€С‚Р°Р±РёСЂСѓРµРј СЂР°Р·РјРµСЂРѕРј РґСЂР°РєРѕРЅР°.
        // sqrt(scale) Сѓ СЃРєРѕСЂРѕСЃС‚Рё вЂ” С‡С‚РѕР±С‹ РїСЂРё Р±РѕР»СЊС€РёС… СЂР°Р·РјРµСЂР°С… СЂР°Р·Р»С‘С‚ РЅРµ СѓС…РѕРґРёР» РІ РєРѕСЃРјРѕСЃ,
        // Р° РѕСЃС‚Р°РІР°Р»СЃСЏ В«С…СЂСѓРјРєР°СЋС‰РёРјВ».
        double posSpread = 0.15D * scale;
        double velSigma = 0.07D * Math.sqrt(scale);

        for (int i = 0; i < count; i++) {
            // РЎС‚Р°СЂС‚РѕРІР°СЏ С‚РѕС‡РєР° вЂ” РїР°СЃС‚СЊ + РЅРµР±РѕР»СЊС€РѕР№ РєСѓР±РёС‡РµСЃРєРёР№ СЂР°Р·Р±СЂРѕСЃ, РєР°Рє Р±СѓРґС‚Рѕ РєСѓСЃРѕС‡РєРё
            // РІС‹Р»РµС‚Р°СЋС‚ РёР· СЂР°Р·РЅС‹С… РјРµСЃС‚ С‡РµР»СЋСЃС‚Рё, Р° РЅРµ РёР· РѕРґРЅРѕР№ С‚РѕС‡РєРё.
            double px = head.x + (random.nextDouble() - 0.5D) * 2.0D * posSpread;
            double py = head.y + (random.nextDouble() - 0.5D) * 2.0D * posSpread;
            double pz = head.z + (random.nextDouble() - 0.5D) * 2.0D * posSpread;

            double mx = random.nextGaussian() * velSigma;
            // Р›С‘РіРєРёР№ Р°РїРІР°СЂРґ-Р±Р°Р№Р°СЃ вЂ” С‡Р°СЃС‚РёС†С‹ РЅРµРјРЅРѕРіРѕ В«Р±СЂС‹Р·Р¶СѓС‚ РІРІРµСЂС…В», РєР°Рє РїСЂРё С‡Р°РІРєР°РЅСЊРµ.
            double my = random.nextGaussian() * velSigma + 0.03D * scale;
            double mz = random.nextGaussian() * velSigma;

            // РћРґРЅР° С‡Р°СЃС‚РёС†Р° Р·Р° РІС‹Р·РѕРІ, СЃРєРѕСЂРѕСЃС‚СЊ Р·Р°РґР°С‘Рј СЂСѓРєР°РјРё (speed=0 вЂ” РёРЅР°С‡Рµ РІРµРєС‚РѕСЂ
            // Р±С‹Р» Р±С‹ РїРµСЂРµРјР°СЃС€С‚Р°Р±РёСЂРѕРІР°РЅ РґРІРёР¶РєРѕРј).
            serverWorld.spawnParticles(effect, px, py, pz, 1, mx, my, mz, 0.0);
        }
    }
}



