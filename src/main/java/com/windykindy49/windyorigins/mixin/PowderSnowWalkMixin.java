package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.common.Powers;
import net.minecraft.block.PowderSnowBlock;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({PowderSnowBlock.class})
public abstract class PowderSnowWalkMixin {
    @Inject(
        method = {"canWalkOnPowderSnow"},
        at = {@At("RETURN")},
        cancellable = true
    )
    private static void windyorigins$frostbornWalk(Entity var0, CallbackInfoReturnable<Boolean> var1) {
        if (!var1.getReturnValueZ() && Powers.has(var0, "cold_blooded")) {
            var1.setReturnValue(true);
        }
    }
}
