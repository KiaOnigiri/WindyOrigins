package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.MirrorEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.TargetPredicate;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({TargetPredicate.class})
public class TargetPredicateMixin {
    @org.spongepowered.asm.mixin.Shadow
    private boolean attackable;

    @Inject(
        method = {"test"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void onTest(@Nullable LivingEntity var1, LivingEntity var2, CallbackInfoReturnable<Boolean> var3) {
        if (var1 != null && var2 != null && ((MirrorEntity)var1).windyorigins$isInMirrorWorld() != ((MirrorEntity)var2).windyorigins$isInMirrorWorld()) {
            var3.setReturnValue(false);
        } else if (this.attackable && var1 != null && var2 != null && com.windykindy49.windyorigins.mirror.MirrorManager.ignoredBy(var1, var2)) {
            var3.setReturnValue(false);
        }
    }
}
