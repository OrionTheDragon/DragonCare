package com.dragoncare.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

public class AshPoisoningEffect extends StatusEffect {

    public AshPoisoningEffect() {
        super(StatusEffectCategory.HARMFUL, 0x4A4A4A); // Темно-серый цвет
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        // Вызываем каждую секунду (каждые 20 тиков)
        return duration % 20 == 0;
    }

    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        if (entity.getWorld().isClient()) {
            return;
        }

        // amplifier: 0 = стадия 1, 1 = стадия 2, 2 = стадия 3, 3 = стадия 4
        if (amplifier == 0) {
            entity.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 40, 0, true, false, false));
            // Легкое головокружение раз в 10 секунд (6 сек = 120 тиков)
            if (entity.age % 200 < 20) {
                entity.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 120, 0, true, false, false));
            }
        } else if (amplifier == 1) {
            entity.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 40, 0, true, false, false));
            entity.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 40, 0, true, false, false));
            
            // Постоянная тошнота
            entity.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 100, 1, true, false, false));
            // 1 урон в секунду
            entity.damage(entity.getDamageSources().magic(), 1.0f);
        } else if (amplifier == 2) {
            entity.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 40, 0, true, false, false));
            entity.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 40, 1, true, false, false));
            entity.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 1, true, false, false));
            
            // Постоянная тошнота
            entity.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 100, 2, true, false, false));
            // 4 урона в секунду
            entity.damage(entity.getDamageSources().magic(), 4.0f);
        } else if (amplifier >= 3) {
            entity.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 40, 0, true, false, false));
            entity.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 40, 4, true, false, false));
            entity.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 3, true, false, false));
            
            // Постоянная тошнота + слепота
            entity.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 100, 3, true, false, false));
            entity.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 100, 0, true, false, false));
            // 8 урона в секунду
            entity.damage(entity.getDamageSources().magic(), 8.0f);
        }
    }
}
