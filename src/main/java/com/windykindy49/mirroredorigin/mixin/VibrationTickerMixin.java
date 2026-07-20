package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.event.Vibrations;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import com.windykindy49.mirroredorigin.util.PhaseScope;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={Vibrations.Ticker.class})
public interface VibrationTickerMixin {
    @Inject(method={"spawnVibrationParticle"}, at={@At(value="HEAD")})
    private static void onSpawnParticleHead(ServerWorld world, Vibrations.ListenerData data, Vibrations.Callback callback, CallbackInfo ci) {
        Entity source = data.getVibration() != null
            ? (Entity)data.getVibration().getEntity(world).orElse(null)
            : null;
        PhaseScope.enterOrKeep(source != null
            ? Boolean.valueOf(((MirrorEntity)source).mirroredorigin$isInMirrorWorld())
            : null);
    }

    @Inject(method={"spawnVibrationParticle"}, at={@At(value="RETURN")})
    private static void onSpawnParticleTail(ServerWorld world, Vibrations.ListenerData data, Vibrations.Callback callback, CallbackInfo ci) {
        PhaseScope.exit();
    }
}

