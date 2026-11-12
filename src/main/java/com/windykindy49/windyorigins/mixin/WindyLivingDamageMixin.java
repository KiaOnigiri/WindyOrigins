package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.common.DamageHooks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({LivingEntity.class})
public abstract class WindyLivingDamageMixin {
    @Inject(
        method = {"damage"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void windyorigins$refuse(DamageSource var1, float var2, CallbackInfoReturnable<Boolean> var3) {
        if (DamageHooks.refuse((LivingEntity)(Object)this, var1)) {
            var3.setReturnValue(false);
        }
    }

    @ModifyVariable(
        method = {"damage"},
        at = @At("HEAD"),
        argsOnly = true
    )
    private float windyorigins$scale(float var1, DamageSource var2) {
        return DamageHooks.scale((LivingEntity)(Object)this, var2, var1);
    }

    @Inject(
        method = {"damage"},
        at = {@At("RETURN")}
    )
    private void windyorigins$after(DamageSource var1, float var2, CallbackInfoReturnable<Boolean> var3) {
        if (var3.getReturnValueZ()) {
            DamageHooks.after((LivingEntity)(Object)this, var1, var2);
        }
    }
}
