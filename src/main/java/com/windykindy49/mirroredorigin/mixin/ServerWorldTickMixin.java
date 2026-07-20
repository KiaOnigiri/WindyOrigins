package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import com.windykindy49.mirroredorigin.util.CrossPhaseEntity;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import com.windykindy49.mirroredorigin.util.NaturalLightning;
import com.windykindy49.mirroredorigin.util.PhaseScope;
import com.windykindy49.mirroredorigin.util.SoundState;
import com.windykindy49.mirroredorigin.util.SpawnPhaseOverride;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * THE fix for sounds leaking across phases.
 *
 * The phase context used to be published by EntityContextMixin around Entity#tick only.
 * That covers far less than it looks: PlayerEntity#tick calls super.tick() -> Entity#tick,
 * which immediately CLEARS the context on the way out, and everything the player does
 * afterwards in the same tick - tickMovement(), picking up items, XP orbs, arrows and
 * tridents - runs with no phase at all. The packet filter then sees a null context, treats
 * the sound as phase-neutral and broadcasts it to both phases. Exactly the reported
 * "pickup sounds are audible through phases".
 *
 * ServerWorld#tickEntity wraps the entire tick of one entity, so publishing the phase here
 * covers the whole thing, including AI goals and collision handlers. tickPassenger does the
 * same for riders, which is why the save/restore has to be nest-safe.
 */
@Mixin(value = {ServerWorld.class})
public class ServerWorldTickMixin {

    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At("HEAD"))
    private void mirroredorigin$resetLeakedScopes(java.util.function.BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        // No entity tick can be in progress here, so a non-empty scope stack is leftover
        // state from a tick that threw. Clearing it stops one aborted tick from mis-phasing
        // everything spawned for the rest of the session.
        PhaseScope.reset();
        SpawnPhaseOverride.reset();
        // SoundState was the one mechanism left out of this sweep. CURRENT_SOUND_SOURCE is a
        // ThreadLocal holding a live Entity: if the pair that clears it is ever skipped, the
        // stale entity both mis-attributes the next sourceless sound to its phase AND is
        // pinned in memory for good, dragging its whole world along through the reference.
        SoundState.reset();
    }

    /**
     * tickChunk is where a thunderstorm decides to strike, lightning rods included. Marking
     * the window lets ServerWorldSpawnMixin tell a bolt sent by the weather apart from one a
     * trident or a command produced, without having to guess from the phase alone.
     */
    @Inject(method = "tickChunk", at = @At("HEAD"))
    private void mirroredorigin$beginWeatherTick(net.minecraft.world.chunk.WorldChunk chunk, int randomTickSpeed, CallbackInfo ci) {
        NaturalLightning.begin();
    }

    @Inject(method = "tickChunk", at = @At("RETURN"))
    private void mirroredorigin$endWeatherTick(net.minecraft.world.chunk.WorldChunk chunk, int randomTickSpeed, CallbackInfo ci) {
        NaturalLightning.end();
    }

    @Inject(method = "tickEntity", at = @At("HEAD"))
    private void mirroredorigin$beginEntityTick(Entity entity, CallbackInfo ci) {
        // A phase-neutral entity publishes no phase, so the thunder it plays and the
        // particles it throws carry no phase either and the packet filter lets them past to
        // everyone. Cheaper and less error-prone than teaching every filter about the flag.
        if (entity instanceof CrossPhaseEntity && ((CrossPhaseEntity) entity).mirroredorigin$isCrossPhase()) {
            PhaseScope.enterOrKeep(null);
            return;
        }
        PhaseScope.enter(((MirrorEntity) entity).mirroredorigin$isInMirrorWorld());
    }

    @Inject(method = "tickEntity", at = @At("RETURN"))
    private void mirroredorigin$endEntityTick(Entity entity, CallbackInfo ci) {
        PhaseScope.exit();
    }

    @Inject(method = "tickPassenger", at = @At("HEAD"))
    private void mirroredorigin$beginPassengerTick(Entity vehicle, Entity passenger, CallbackInfo ci) {
        PhaseScope.enter(((MirrorEntity) passenger).mirroredorigin$isInMirrorWorld());
    }

    @Inject(method = "tickPassenger", at = @At("RETURN"))
    private void mirroredorigin$endPassengerTick(Entity vehicle, Entity passenger, CallbackInfo ci) {
        PhaseScope.exit();
    }
}
