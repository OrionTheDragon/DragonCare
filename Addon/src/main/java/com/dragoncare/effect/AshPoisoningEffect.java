package com.dragoncare.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

public class AshPoisoningEffect extends StatusEffect {

    // Уникальные ID для attribute модификаторов
    private static final Identifier MINING_SPEED_ID = Identifier.of("dragoncare", "ash_mining_debuff");
    private static final Identifier ATTACK_DAMAGE_ID = Identifier.of("dragoncare", "ash_attack_debuff");
    private static final Identifier MOVEMENT_SPEED_ID = Identifier.of("dragoncare", "ash_speed_debuff");

    public AshPoisoningEffect() {
        super(StatusEffectCategory.HARMFUL, 0x4A4A4A); // Темно-серый цвет
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        // Вызываем каждую секунду (каждые 20 тиков)
        return duration % 20 == 0;
    }

    @Override
    public boolean applyUpdateEffect(LivingEntity entity, int amplifier) {
        if (entity.getWorld().isClient()) {
            return true;
        }

        // amplifier: 0 = стадия 1, 1 = стадия 2, 2 = стадия 3, 3 = стадия 4
        if (amplifier == 0) {
            // Усталость при добыче (замедление копания) — через атрибут
            applyMiningDebuff(entity, -0.3); // -30% скорость копания
            removeAttackDebuff(entity);
            removeSpeedDebuff(entity);
            // Легкое головокружение раз в 10 секунд (6 сек = 120 тиков)
            if (entity.age % 200 < 20) {
                entity.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 120, 0, true, false, false));
            }
        } else if (amplifier == 1) {
            applyMiningDebuff(entity, -0.3);
            applyAttackDebuff(entity, -4.0); // Слабость: -4 урона
            removeSpeedDebuff(entity);
            // Постоянная тошнота
            entity.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 100, 1, true, false, false));
            // 1 урон в секунду
            entity.damage(entity.getDamageSources().magic(), 1.0f);
        } else if (amplifier == 2) {
            applyMiningDebuff(entity, -0.3);
            applyAttackDebuff(entity, -8.0); // Слабость II: -8 урона
            applySpeedDebuff(entity, -0.3); // Замедление II: -30%
            // Постоянная тошнота
            entity.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 100, 2, true, false, false));
            // 4 урона в секунду
            entity.damage(entity.getDamageSources().magic(), 4.0f);
        } else if (amplifier >= 3) {
            applyMiningDebuff(entity, -0.3);
            applyAttackDebuff(entity, -20.0); // Слабость V: -20 урона (~90%+ снижение)
            applySpeedDebuff(entity, -0.6); // Замедление V: -60% (почти не может ходить)
            // Постоянная тошнота + слепота
            entity.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 100, 3, true, false, false));
            entity.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 100, 0, true, false, false));
            // 8 урона в секунду
            entity.damage(entity.getDamageSources().magic(), 8.0f);
        }
        return true;
    }

    @Override
    public void onRemoved(net.minecraft.entity.attribute.AttributeContainer attributes) {
        // Очищаем все наши модификаторы при снятии эффекта
        var mining = attributes.getCustomInstance(EntityAttributes.PLAYER_BLOCK_BREAK_SPEED);
        if (mining != null) mining.removeModifier(MINING_SPEED_ID);
        var attack = attributes.getCustomInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (attack != null) attack.removeModifier(ATTACK_DAMAGE_ID);
        var speed = attributes.getCustomInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(MOVEMENT_SPEED_ID);
    }

    // === Attribute Modifier методы ===

    private void applyMiningDebuff(LivingEntity entity, double amount) {
        var attr = entity.getAttributeInstance(EntityAttributes.PLAYER_BLOCK_BREAK_SPEED);
        if (attr != null) {
            attr.removeModifier(MINING_SPEED_ID);
            attr.addTemporaryModifier(new EntityAttributeModifier(
                    MINING_SPEED_ID, amount, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private void removeMiningDebuff(LivingEntity entity) {
        var attr = entity.getAttributeInstance(EntityAttributes.PLAYER_BLOCK_BREAK_SPEED);
        if (attr != null) attr.removeModifier(MINING_SPEED_ID);
    }

    private void applyAttackDebuff(LivingEntity entity, double amount) {
        var attr = entity.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (attr != null) {
            attr.removeModifier(ATTACK_DAMAGE_ID);
            attr.addTemporaryModifier(new EntityAttributeModifier(
                    ATTACK_DAMAGE_ID, amount, EntityAttributeModifier.Operation.ADD_VALUE));
        }
    }

    private void removeAttackDebuff(LivingEntity entity) {
        var attr = entity.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (attr != null) attr.removeModifier(ATTACK_DAMAGE_ID);
    }

    private void applySpeedDebuff(LivingEntity entity, double amount) {
        var attr = entity.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (attr != null) {
            attr.removeModifier(MOVEMENT_SPEED_ID);
            attr.addTemporaryModifier(new EntityAttributeModifier(
                    MOVEMENT_SPEED_ID, amount, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private void removeSpeedDebuff(LivingEntity entity) {
        var attr = entity.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (attr != null) attr.removeModifier(MOVEMENT_SPEED_ID);
    }
}

