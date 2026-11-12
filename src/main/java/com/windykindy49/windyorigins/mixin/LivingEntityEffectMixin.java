package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.MirrorEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({LivingEntity.class})
public class LivingEntityEffectMixin {
    @Inject(
        method = {"addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)Z"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void onAddStatusEffect(StatusEffectInstance var1, @Nullable Entity var2, CallbackInfoReturnable<Boolean> var3) {
        if (var2 != null && ((MirrorEntity)(Object)this).windyorigins$isInMirrorWorld() != ((MirrorEntity)var2).windyorigins$isInMirrorWorld()) {
            var3.setReturnValue(false);
        }
    }
}
