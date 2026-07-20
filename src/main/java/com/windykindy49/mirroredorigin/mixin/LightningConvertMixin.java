package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.passive.MooshroomEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import com.windykindy49.mirroredorigin.util.CrossPhaseEntity;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import com.windykindy49.mirroredorigin.util.SpawnPhaseOverride;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the phase across lightning transformations.
 *
 * MobConvertMixin only covers transformations that go through MobEntity#convertTo. The mobs
 * listed here do NOT use it: their onStruckByLightning builds the replacement by hand and
 * spawns it directly, so the phase was lost. Pigs were fixed first and confirmed working;
 * villagers and mooshrooms take exactly the same route and need the same treatment.
 *
 * require = 0 per target: a mob that does not override onStruckByLightning simply gets no
 * injection instead of taking the game down.
 */
@Mixin(value = {PigEntity.class, VillagerEntity.class, MooshroomEntity.class, CreeperEntity.class})
public abstract class LightningConvertMixin {
    @Unique
    private boolean mirroredorigin$scoped;

    @Inject(method = "onStruckByLightning", at = @At("HEAD"), cancellable = true, require = 0)
    private void mirroredorigin$beginStrike(ServerWorld world, LightningEntity lightning, CallbackInfo ci) {
        // A bolt must not transform something it cannot even touch. Lightning damage was
        // already refused by LivingEntityMixin, but transformation never goes through
        // damage(), so a mirror-phase bolt from a channelling trident still turned an
        // overworld-phase pig into a zombified piglin - and natural weather, which is always
        // overworld phase, did the same to mirror-phase animals.
        // A storm transforms in both worlds; only an owned bolt is confined to its phase.
        if (!((CrossPhaseEntity) lightning).mirroredorigin$isCrossPhase()
            && ((MirrorEntity) (Object) this).mirroredorigin$isInMirrorWorld()
                != ((MirrorEntity) lightning).mirroredorigin$isInMirrorWorld()) {
            ci.cancel();
            return;
        }
        SpawnPhaseOverride.enter(((MirrorEntity) (Object) this).mirroredorigin$isInMirrorWorld());
        this.mirroredorigin$scoped = true;
    }

    // The flag matters: a cancelled HEAD returns past this callback, so exit() must never run
    // for a strike whose scope was never opened, or it would unwind somebody else's frame.
    @Inject(method = "onStruckByLightning", at = @At("RETURN"), require = 0)
    private void mirroredorigin$endStrike(ServerWorld world, LightningEntity lightning, CallbackInfo ci) {
        if (this.mirroredorigin$scoped) {
            this.mirroredorigin$scoped = false;
            SpawnPhaseOverride.exit();
        }
    }
}
