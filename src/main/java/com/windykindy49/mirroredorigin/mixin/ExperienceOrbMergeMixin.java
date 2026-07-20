package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import com.windykindy49.mirroredorigin.util.MirrorContext;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The same leak as with items, but worse, because experience is a number rather than a
 * stack: orbs from both phases merged and the combined amount went to whoever reached it
 * first, so a kill in one phase could hand its levels to a player in the other.
 *
 * Two separate paths:
 *
 * 1. isMergeable(other) - orbs consolidating while they lie on the ground. A plain phase
 *    comparison is enough, both entities exist.
 *
 * 2. wasMergedIntoExistingOrb(...) - a NEW orb folding itself into an existing one at spawn
 *    time. Here the orb does not exist yet, so the only thing identifying its phase is the
 *    spawn context. Rather than guess which of the candidate orbs are eligible, mirror-phase
 *    spawns are simply refused the shortcut: they get their own entity, and path 1 merges
 *    them with their own phase a moment later. Slightly more orbs for a few ticks, and no
 *    possibility of experience crossing over.
 */
@Mixin(value = {ExperienceOrbEntity.class})
public abstract class ExperienceOrbMergeMixin {
    @Shadow
    private PlayerEntity target;

    /**
     * An orb homes in on the nearest player within eight blocks, and that search ignored
     * phases. A mirror-phase orb would chase an overworld-phase player it can never be
     * collected by, hover at their feet forever, and look to its own phase like experience
     * drifting off towards nothing at all.
     *
     * Clearing the target rather than filtering the search covers every way vanilla might
     * assign it, and the orb simply picks a valid one on its next look.
     */
    @Inject(method = "tick", at = @At("RETURN"))
    private void mirroredorigin$dropCrossPhaseTarget(CallbackInfo ci) {
        if (this.target != null
            && ((MirrorEntity) (Object) this).mirroredorigin$isInMirrorWorld()
                != ((MirrorEntity) this.target).mirroredorigin$isInMirrorWorld()) {
            this.target = null;
        }
    }


    @Inject(method = "isMergeable(Lnet/minecraft/entity/ExperienceOrbEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void mirroredorigin$onlyMergeSamePhase(ExperienceOrbEntity other, CallbackInfoReturnable<Boolean> cir) {
        if (((MirrorEntity) (Object) this).mirroredorigin$isInMirrorWorld()
            != ((MirrorEntity) other).mirroredorigin$isInMirrorWorld()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "wasMergedIntoExistingOrb", at = @At("HEAD"), cancellable = true, require = 0)
    private static void mirroredorigin$noCrossPhaseSpawnMerge(ServerWorld world, Vec3d pos, int amount, CallbackInfoReturnable<Boolean> cir) {
        Boolean context = MirrorContext.get();
        if (context != null && context.booleanValue()) {
            cir.setReturnValue(false);
        }
    }
}
