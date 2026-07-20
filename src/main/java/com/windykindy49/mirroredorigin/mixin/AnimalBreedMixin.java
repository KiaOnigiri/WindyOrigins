package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.server.world.ServerWorld;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import com.windykindy49.mirroredorigin.util.SpawnPhaseOverride;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bred babies used to always appear in the overworld phase.
 *
 * This targeted the WRONG overload before. In 1.20.1 there are two:
 *
 *   breed(ServerWorld, AnimalEntity)                  = method_24650
 *   breed(ServerWorld, AnimalEntity, PassiveEntity)   = method_49794
 *
 * The 3-argument one only handles statistics, breeding age and the XP orb - it never
 * touches the child. The 2-argument one is the real thing: it calls createChild(), then
 * the 3-argument overload, and only AFTERWARDS does world.spawnEntityAndPassengers(baby).
 * Scoping the override to the 3-argument call therefore ended exactly one instruction
 * before the baby was spawned, so the override was already gone and the child fell back to
 * the overworld phase.
 *
 * Wrapping the 2-argument overload covers the child and the breeding XP orb alike.
 */
@Mixin(value = {AnimalEntity.class})
public abstract class AnimalBreedMixin {
    @Inject(
        method = "breed(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/passive/AnimalEntity;)V",
        at = @At(value = "HEAD")
    )
    private void mirroredorigin$beginBreed(ServerWorld world, AnimalEntity other, CallbackInfo ci) {
        SpawnPhaseOverride.enter(((MirrorEntity) (Object) this).mirroredorigin$isInMirrorWorld());
    }

    @Inject(
        method = "breed(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/passive/AnimalEntity;)V",
        at = @At(value = "RETURN")
    )
    private void mirroredorigin$endBreed(ServerWorld world, AnimalEntity other, CallbackInfo ci) {
        SpawnPhaseOverride.exit();
    }
}
