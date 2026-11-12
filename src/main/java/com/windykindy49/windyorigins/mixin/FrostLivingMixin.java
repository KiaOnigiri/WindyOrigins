package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.common.Powers;
import com.windykindy49.windyorigins.common.WindyEffects;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({LivingEntity.class})
public abstract class FrostLivingMixin {
    @Inject(
        method = {"canFreeze"},
        at = {@At("RETURN")},
        cancellable = true
    )
    private void windyorigins$coldBlooded(CallbackInfoReturnable<Boolean> var1) {
        if (var1.getReturnValueZ() && Powers.has((LivingEntity)(Object)this, "cold_blooded")) {
            var1.setReturnValue(false);
        }
    }

    @Inject(
        method = {"getJumpVelocity"},
        at = {@At("RETURN")},
        cancellable = true
    )
    private void windyorigins$noJumpInCoffin(CallbackInfoReturnable<Float> var1) {
        if (((LivingEntity)(Object)this).hasStatusEffect(WindyEffects.ICE_COFFIN) || ((LivingEntity)(Object)this).hasStatusEffect(WindyEffects.DREAMING)) {
            var1.setReturnValue(0.0F);
        }
    }
}
