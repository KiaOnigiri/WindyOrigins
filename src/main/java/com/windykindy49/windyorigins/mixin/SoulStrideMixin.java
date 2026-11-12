package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.common.Powers;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Soul Stride: soul sand does not drag at a Revenant's feet. */
@Mixin({LivingEntity.class})
public abstract class SoulStrideMixin {
    @Shadow
    protected abstract boolean isOnSoulSpeedBlock();

    @Inject(
        method = {"getVelocityMultiplier"},
        at = {@At("RETURN")},
        cancellable = true
    )
    private void windyorigins$soulStride(CallbackInfoReturnable<Float> var1) {
        if (var1.getReturnValueF() < 1.0F && this.isOnSoulSpeedBlock() && Powers.has((LivingEntity)(Object)this, "soul_stride")) {
            var1.setReturnValue(1.0F);
        }
    }
}
