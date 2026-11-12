package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.CrossPhaseEntity;
import com.windykindy49.windyorigins.util.MirrorEntity;
import com.windykindy49.windyorigins.util.NaturalLightning;
import com.windykindy49.windyorigins.util.PhaseScope;
import com.windykindy49.windyorigins.util.SoundState;
import com.windykindy49.windyorigins.util.SpawnPhaseOverride;
import java.util.function.BooleanSupplier;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ServerWorld.class})
public class ServerWorldTickMixin {
    @Inject(
        method = {"tick(Ljava/util/function/BooleanSupplier;)V"},
        at = {@At("HEAD")}
    )
    private void windyorigins$resetLeakedScopes(BooleanSupplier var1, CallbackInfo var2) {
        PhaseScope.reset();
        SpawnPhaseOverride.reset();
        SoundState.reset();
    }

    @Inject(
        method = {"tickChunk"},
        at = {@At("HEAD")}
    )
    private void windyorigins$beginWeatherTick(WorldChunk var1, int var2, CallbackInfo var3) {
        NaturalLightning.begin();
    }

    @Inject(
        method = {"tickChunk"},
        at = {@At("RETURN")}
    )
    private void windyorigins$endWeatherTick(WorldChunk var1, int var2, CallbackInfo var3) {
        NaturalLightning.end();
    }

    @Inject(
        method = {"tickEntity"},
        at = {@At("HEAD")}
    )
    private void windyorigins$beginEntityTick(Entity var1, CallbackInfo var2) {
        if (var1 instanceof CrossPhaseEntity && ((CrossPhaseEntity)var1).windyorigins$isCrossPhase()) {
            PhaseScope.enterOrKeep(null);
        } else {
            PhaseScope.enter(((MirrorEntity)var1).windyorigins$isInMirrorWorld());
        }
    }

    @Inject(
        method = {"tickEntity"},
        at = {@At("RETURN")}
    )
    private void windyorigins$endEntityTick(Entity var1, CallbackInfo var2) {
        PhaseScope.exit();
    }

    @Inject(
        method = {"tickPassenger"},
        at = {@At("HEAD")}
    )
    private void windyorigins$beginPassengerTick(Entity var1, Entity var2, CallbackInfo var3) {
        PhaseScope.enter(((MirrorEntity)var2).windyorigins$isInMirrorWorld());
    }

    @Inject(
        method = {"tickPassenger"},
        at = {@At("RETURN")}
    )
    private void windyorigins$endPassengerTick(Entity var1, Entity var2, CallbackInfo var3) {
        PhaseScope.exit();
    }
}
