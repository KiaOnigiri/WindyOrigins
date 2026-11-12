package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.common.Powers;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({LivingEntity.class})
public abstract class SleepAnywhereMixin {
    @Inject(
        method = {"isSleepingInBed"},
        at = {@At("RETURN")},
        cancellable = true
    )
    private void windyorigins$groundIsABed(CallbackInfoReturnable<Boolean> var1) {
        LivingEntity var2 = (LivingEntity)(Object)this;
        if (!var1.getReturnValueZ() && var2 instanceof PlayerEntity && var2.isSleeping() && Powers.has(var2, "sleep_anywhere")) {
            var1.setReturnValue(true);
        }
    }
}
