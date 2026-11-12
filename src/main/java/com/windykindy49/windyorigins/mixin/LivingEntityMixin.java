package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.MirrorEntity;
import com.windykindy49.windyorigins.util.SidedBridge;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({LivingEntity.class})
public class LivingEntityMixin {
    @Inject(
        method = {"damage"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void onDamage(DamageSource var1, float var2, CallbackInfoReturnable<Boolean> var3) {
        Entity var4 = var1.getAttacker();
        if (var4 != null && ((MirrorEntity)(Object)this).windyorigins$isInMirrorWorld() != ((MirrorEntity)var4).windyorigins$isInMirrorWorld()) {
            var3.setReturnValue(false);
        }
    }

    @Inject(
        method = {"tickStatusEffects"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void onTickStatusEffects(CallbackInfo var1) {
        LivingEntity var3 = (LivingEntity)(Object)this;
        Entity var2;
        if (var3.getWorld().isClient()
            && (var2 = SidedBridge.camera()) != null
            && ((MirrorEntity)(Object)this).windyorigins$isInMirrorWorld() != ((MirrorEntity)var2).windyorigins$isInMirrorWorld()) {
            var1.cancel();
        }
    }
}
