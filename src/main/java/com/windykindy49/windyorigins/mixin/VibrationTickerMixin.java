package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.MirrorEntity;
import com.windykindy49.windyorigins.util.PhaseScope;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.event.listener.Vibration;
import net.minecraft.world.event.listener.VibrationListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({VibrationListener.class})
public class VibrationTickerMixin {
    @Inject(
        method = {"method_45495"},
        at = {@At("HEAD")}
    )
    private void onSpawnParticleHead(ServerWorld var1, Vibration var2, CallbackInfo var3) {
        Entity var4 = var2 != null ? var2.getEntity(var1).orElse(null) : null;
        PhaseScope.enterOrKeep(var4 != null ? ((MirrorEntity)var4).windyorigins$isInMirrorWorld() : null);
    }

    @Inject(
        method = {"method_45495"},
        at = {@At("RETURN")}
    )
    private void onSpawnParticleTail(ServerWorld var1, Vibration var2, CallbackInfo var3) {
        PhaseScope.exit();
    }
}
