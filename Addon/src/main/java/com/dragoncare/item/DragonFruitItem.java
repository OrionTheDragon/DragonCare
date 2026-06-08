package com.dragoncare.item;

import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import com.dragoncare.taming.BondManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
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
 * Драконий фрукт.
 * <ul>
 *   <li>Игрок может съесть как обычную еду (6 голода, 3 сытости — задаётся в {@link ModItems}
 *       через {@code FoodComponent}).</li>
 *   <li>Применяя на собственном прирученном драконе: даёт +6 к привязанности
 *       (в обход 10-минутного кулдауна {@link BondManager#FEED_CAP_USES}),
 *       растит дракона на 1 день и пополняет ему голод (как ванильный
 *       {@code iceandfire:dragon_meal}).</li>
 * </ul>
 */
public class DragonFruitItem extends BlockItem {

    /** Сколько очков привязанности даёт одно скармливание фрукта. */
    public static final int BOND_PER_FEED = 6;
    /** На сколько растёт дракон (в игровых днях) при скармливании. */
    private static final int GROWTH_DAYS = 1;
    /** Сколько единиц голода даёт фрукт дракону при скармливании. */
    private static final int HUNGER_GAIN = 20;
    /** Хард-кап голода в CE. */
    private static final int HUNGER_CAP = 100;

    public DragonFruitItem(Block block, Settings settings) {
        super(block, settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        tooltip.add(Text.translatable("item.dragoncare.dragon_fruit.desc_0").formatted(Formatting.GRAY));
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (!(entity instanceof DragonBaseEntity dragon)) {
            return ActionResult.PASS;
        }
        if (dragon.isModelDead()) {
            return ActionResult.PASS;
        }
        // Кормить можно только своего прирученного дракона. Иначе — даём fall-through на еду.
        if (dragon.getOwnerUuid() == null || !dragon.getOwnerUuid().equals(user.getUuid())) {
            return ActionResult.PASS;
        }
        if (user.getWorld().isClient) {
            return ActionResult.SUCCESS;
        }
        if (!(user instanceof ServerPlayerEntity serverPlayer)) {
            return ActionResult.PASS;
        }

        // Привязанность — в обход 10-минутного кулдауна
        BondManager.onFeedBypass(serverPlayer, dragon, BOND_PER_FEED);

        // Рост + еда (как iceandfire:dragon_meal в CE)
        try {
            dragon.growDragon(GROWTH_DAYS);
        } catch (Throwable ignored) {
            // На случай несовместимой ветки CE — приоритет у привязанности
        }
        try {
            dragon.setHunger(Math.min(HUNGER_CAP, dragon.getHunger() + HUNGER_GAIN));
        } catch (Throwable ignored) {
            // см. выше
        }

        // Звук + частицы (как при кормлении iceandfire:dragon_meal),
        // но частицы заменены на куски самого фрукта — они красные.
        playFeedFx(dragon, stack);

        if (!user.isCreative()) stack.decrement(1);
        return ActionResult.SUCCESS;
    }

    /**
     * Воспроизводит на дракона тот же звук, что и при поедании {@code iceandfire:dragon_meal}
     * ({@link SoundEvents#ENTITY_GENERIC_EAT}), и спавнит из его пасти облако частиц
     * {@link ItemStackParticleEffect} с куском драконьего фрукта (даёт красный цвет
     * под цвет самого фрукта).
     *
     * <p>За основу взят {@code DragonBaseEntity#spawnItemCrackParticles(Item)} из CE
     * (точка спавна — {@link DragonBaseEntity#getHeadPosition()}), но количество, разлёт
     * и стартовый разброс масштабируются по {@link DragonBaseEntity#getRenderSize()},
     * чтобы у крупного взрослого дракона эффект выглядел соразмерно туше, а не как
     * пара зёрнышек у пасти.</p>
     */
    private static void playFeedFx(DragonBaseEntity dragon, ItemStack fruitStack) {
        World world = dragon.getWorld();

        // Звук — тот же, что в CE для dragon_meal.
        // Entity.playSound — публичный метод, в отличие от protected getSoundVolume/Pitch.
        dragon.playSound(SoundEvents.ENTITY_GENERIC_EAT, 1.0F, 1.0F);

        if (!(world instanceof ServerWorld serverWorld)) return;

        // ItemStackParticleEffect с фруктом — текстура частицы берётся из спрайта предмета
        // (т.е. красная). На сервере используем spawnParticles, чтобы пакет ушёл всем клиентам.
        ItemStackParticleEffect effect = new ItemStackParticleEffect(ParticleTypes.ITEM, fruitStack);

        // getHeadPosition() — публичный метод DragonBaseEntity, возвращает реальную точку
        // пасти с учётом позы/анимации дракона; именно его дёргает CE для dragon_meal.
        Vec3d head = dragon.getHeadPosition();
        Random random = dragon.getRandom();

        // getRenderSize() в CE: ~3 у новорождённого, до ~20 у взрослого 5-й стадии.
        // Базу 3 берём как «единицу» — у малыша scale≈1, у крупного ≈6-7.
        float renderSize = Math.max(1.5F, dragon.getRenderSize());
        float scale = Math.max(0.6F, renderSize / 3.0F);

        // Кол-во частиц: малыш ~13, взрослый ~40+ (визуально заметнее на большой пасти).
        int count = (int) (10 + scale * 5.0F);

        // Стартовый разброс вокруг пасти и сигма по скорости масштабируем размером дракона.
        // sqrt(scale) у скорости — чтобы при больших размерах разлёт не уходил в космос,
        // а оставался «хрумкающим».
        double posSpread = 0.15D * scale;
        double velSigma = 0.07D * Math.sqrt(scale);

        for (int i = 0; i < count; i++) {
            // Стартовая точка — пасть + небольшой кубический разброс, как будто кусочки
            // вылетают из разных мест челюсти, а не из одной точки.
            double px = head.x + (random.nextDouble() - 0.5D) * 2.0D * posSpread;
            double py = head.y + (random.nextDouble() - 0.5D) * 2.0D * posSpread;
            double pz = head.z + (random.nextDouble() - 0.5D) * 2.0D * posSpread;

            double mx = random.nextGaussian() * velSigma;
            // Лёгкий апвард-байас — частицы немного «брызжут вверх», как при чавканье.
            double my = random.nextGaussian() * velSigma + 0.03D * scale;
            double mz = random.nextGaussian() * velSigma;

            // Одна частица за вызов, скорость задаём руками (speed=0 — иначе вектор
            // был бы перемасштабирован движком).
            serverWorld.spawnParticles(effect, px, py, pz, 1, mx, my, mz, 0.0);
        }
    }
}
