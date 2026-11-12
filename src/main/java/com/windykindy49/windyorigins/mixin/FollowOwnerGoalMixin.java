package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.MirrorEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.entity.passive.TameableEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({FollowOwnerGoal.class})
public abstract class FollowOwnerGoalMixin {
    @Shadow
    @Final
    private TameableEntity tameable;
    @Shadow
    private LivingEntity owner;

    @Inject(
        method = {"canStart"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void windyorigins$blockCrossPhaseStart(CallbackInfoReturnable<Boolean> var1) {
        LivingEntity var2 = this.tameable.getOwner();
        if (var2 != null && this.windyorigins$differentPhase(var2)) {
            var1.setReturnValue(false);
        }
    }

    @Inject(
        method = {"shouldContinue"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void windyorigins$blockCrossPhaseContinue(CallbackInfoReturnable<Boolean> var1) {
        LivingEntity var2 = this.owner != null ? this.owner : this.tameable.getOwner();
        if (var2 != null && this.windyorigins$differentPhase(var2)) {
            var1.setReturnValue(false);
        }
    }

    private boolean windyorigins$differentPhase(LivingEntity var1) {
        return ((MirrorEntity)(Object)this.tameable).windyorigins$isInMirrorWorld() != ((MirrorEntity)var1).windyorigins$isInMirrorWorld();
    }
}
