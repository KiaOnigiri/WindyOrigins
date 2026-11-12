package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.common.Powers;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.FoxEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({FoxEntity.class})
public abstract class LullabyFoxMixin {
    @Inject(
        method = {"method_18253(Lnet/minecraft/entity/Entity;)Z"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private static void windyorigins$lullaby(Entity var0, CallbackInfoReturnable<Boolean> var1) {
        if (Powers.has(var0, "lullaby")) {
            var1.setReturnValue(false);
        }
    }
}
