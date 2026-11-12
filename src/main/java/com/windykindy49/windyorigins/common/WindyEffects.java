package com.windykindy49.windyorigins.common;

import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class WindyEffects {
    public static final StatusEffect ICE_COFFIN = reg(
        "ice_coffin",
        new WindyEffects.WindyEffect(StatusEffectCategory.BENEFICIAL, 10479871)
            .addAttributeModifier(EntityAttributes.GENERIC_MOVEMENT_SPEED, "3b4e2a90-1c0f-4c8e-9a51-6e0c7d1f2a01", -1.0, Operation.MULTIPLY_TOTAL)
    );
    public static final StatusEffect FOLDED = reg("folded", new WindyEffects.WindyEffect(StatusEffectCategory.BENEFICIAL, 8084735));
    public static final StatusEffect BAT_FORM = reg("bat_form", new WindyEffects.WindyEffect(StatusEffectCategory.BENEFICIAL, 3806232));
    public static final StatusEffect TASTE = reg("blood_taste", new WindyEffects.WindyEffect(StatusEffectCategory.BENEFICIAL, 10489884));
    public static final StatusEffect POSSESSED = reg("possessed", new WindyEffects.WindyEffect(StatusEffectCategory.NEUTRAL, 6281448));
    public static final StatusEffect CYBERPSYCHOSIS = reg("cyberpsychosis", new WindyEffects.WindyEffect(StatusEffectCategory.HARMFUL, 15744200));
    public static final StatusEffect LULLED = reg(
        "lulled",
        new WindyEffects.WindyEffect(StatusEffectCategory.HARMFUL, 10259680)
            .addAttributeModifier(EntityAttributes.GENERIC_MOVEMENT_SPEED, "5b7c1e3a-2d4f-4a6b-8c9d-0e1f2a3b4c5d", -0.4, Operation.MULTIPLY_TOTAL)
            .addAttributeModifier(EntityAttributes.GENERIC_ATTACK_SPEED, "6c8d2f4b-3e5a-4b7c-9d0e-1f2a3b4c5d6e", -0.4, Operation.MULTIPLY_TOTAL)
    );
    public static final StatusEffect DREAMING = reg(
        "dreaming",
        new WindyEffects.WindyEffect(StatusEffectCategory.HARMFUL, 11836671)
            .addAttributeModifier(EntityAttributes.GENERIC_MOVEMENT_SPEED, "8d1e4b2c-3f5a-4c6b-9d7e-2a1b0c9d8e01", -1.0, Operation.MULTIPLY_TOTAL)
    );
    public static final StatusEffect DREAM_SICKNESS = reg("dream_sickness", new WindyEffects.WindyEffect(StatusEffectCategory.HARMFUL, 6970061));
    public static final StatusEffect NIGHTMARE = reg("nightmare", new WindyEffects.WindyEffect(StatusEffectCategory.HARMFUL, 2759232));
    public static final StatusEffect DREAM_FOG = reg("dream_fog", new WindyEffects.WindyEffect(StatusEffectCategory.HARMFUL, 12876426));
    public static final StatusEffect VENGEANCE = reg("vengeance", new WindyEffects.WindyEffect(StatusEffectCategory.BENEFICIAL, 4094975));

    public static final StatusEffect GUILT = reg("guilt", new WindyEffects.WindyEffect(StatusEffectCategory.HARMFUL, 9118623));

    private WindyEffects() {
    }

    private static StatusEffect reg(String var0, StatusEffect var1) {
        return Registry.register(Registries.STATUS_EFFECT, new Identifier("windyorigins", var0), var1);
    }

    public static void init() {
    }

    public static class WindyEffect extends StatusEffect {
        public WindyEffect(StatusEffectCategory var1, int var2) {
            super(var1, var2);
        }
    }
}
