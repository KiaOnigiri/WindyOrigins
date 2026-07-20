package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.entity.passive.TameableEntity;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A tamed mob must not follow or teleport to an owner standing in the other phase.
 * It stays put and idles, exactly as if the owner were offline or in another world.
 */
@Mixin(value={FollowOwnerGoal.class})
public abstract class FollowOwnerGoalMixin {
    @Shadow @Final
    private TameableEntity tameable;

    @Shadow
    private LivingEntity owner;

    @Inject(method={"canStart"}, at={@At(value="HEAD")}, cancellable=true)
    private void mirroredorigin$blockCrossPhaseStart(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity current = this.tameable.getOwner();
        if (current != null && this.mirroredorigin$differentPhase(current)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method={"shouldContinue"}, at={@At(value="HEAD")}, cancellable=true)
    private void mirroredorigin$blockCrossPhaseContinue(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity current = this.owner != null ? this.owner : this.tameable.getOwner();
        if (current != null && this.mirroredorigin$differentPhase(current)) {
            cir.setReturnValue(false);
        }
    }

    private boolean mirroredorigin$differentPhase(LivingEntity other) {
        return ((MirrorEntity)this.tameable).mirroredorigin$isInMirrorWorld()
            != ((MirrorEntity)other).mirroredorigin$isInMirrorWorld();
    }
}
