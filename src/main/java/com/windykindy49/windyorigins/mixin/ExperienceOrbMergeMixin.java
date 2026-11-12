package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.MirrorContext;
import com.windykindy49.windyorigins.util.MirrorEntity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ExperienceOrbEntity.class})
public abstract class ExperienceOrbMergeMixin {
    @Shadow
    private PlayerEntity target;

    @Inject(
        method = {"tick"},
        at = {@At("RETURN")}
    )
    private void windyorigins$dropCrossPhaseTarget(CallbackInfo var1) {
        if (this.target != null && ((MirrorEntity)(Object)this).windyorigins$isInMirrorWorld() != ((MirrorEntity)(Object)this.target).windyorigins$isInMirrorWorld()) {
            this.target = null;
        }
    }

    @Inject(
        method = {"isMergeable(Lnet/minecraft/entity/ExperienceOrbEntity;)Z"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void windyorigins$onlyMergeSamePhase(ExperienceOrbEntity var1, CallbackInfoReturnable<Boolean> var2) {
        if (((MirrorEntity)(Object)this).windyorigins$isInMirrorWorld() != ((MirrorEntity)var1).windyorigins$isInMirrorWorld()) {
            var2.setReturnValue(false);
        }
    }

    @Inject(
        method = {"wasMergedIntoExistingOrb"},
        at = {@At("HEAD")},
        cancellable = true,
        require = 0
    )
    private static void windyorigins$noCrossPhaseSpawnMerge(ServerWorld var0, Vec3d var1, int var2, CallbackInfoReturnable<Boolean> var3) {
        Boolean var4 = MirrorContext.get();
        if (var4 != null && var4) {
            var3.setReturnValue(false);
        }
    }
}
