package com.windykindy49.windyorigins.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.windykindy49.windyorigins.common.Powers;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Beacon: crouching does not halve the distance a Luminian's name tag is drawn at. */
@Mixin({LivingEntityRenderer.class})
public abstract class LuminianNameRangeMixin {
    @ModifyExpressionValue(
        method = {"hasLabel(Lnet/minecraft/entity/LivingEntity;)Z"},
        at = {@At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;isSneaky()Z")}
    )
    private boolean windyorigins$beaconRange(boolean var1, LivingEntity var2) {
        return var1 && !Powers.has(var2, "beacon");
    }
}
