package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.MirrorEntity;
import com.windykindy49.windyorigins.util.PhaseScope;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.event.Vibrations.Callback;
import net.minecraft.world.event.Vibrations.ListenerData;
import net.minecraft.world.event.Vibrations.Ticker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Ticker.class})
public interface VibrationTickerMixin {
    @Inject(
        method = {"spawnVibrationParticle"},
        at = {@At("HEAD")}
    )
    private static void onSpawnParticleHead(ServerWorld var0, ListenerData var1, Callback var2, CallbackInfo var3) {
        Entity var4 = var1.getVibration() != null ? var1.getVibration().getEntity(var0).orElse(null) : null;
        PhaseScope.enterOrKeep(var4 != null ? ((MirrorEntity)var4).windyorigins$isInMirrorWorld() : null);
    }

    @Inject(
        method = {"spawnVibrationParticle"},
        at = {@At("RETURN")}
    )
    private static void onSpawnParticleTail(ServerWorld var0, ListenerData var1, Callback var2, CallbackInfo var3) {
        PhaseScope.exit();
    }
}
