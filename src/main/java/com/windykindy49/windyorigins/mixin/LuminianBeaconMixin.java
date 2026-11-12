package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.common.Powers;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({LivingEntity.class})
public abstract class LuminianBeaconMixin {
    @Inject(
        method = {"getAttackDistanceScalingFactor"},
        at = {@At("RETURN")},
        cancellable = true
    )
    private void windyorigins$beacon(Entity var1, CallbackInfoReturnable<Double> var2) {
        LivingEntity var3 = (LivingEntity)(Object)this;
        if (Powers.has(var3, "beacon")) {
            double var4 = var2.getReturnValueD();
            if (var3.isSneaky()) {
                var4 /= 0.8;
            }

            var2.setReturnValue(var4 * 1.5);
        }
    }
}
