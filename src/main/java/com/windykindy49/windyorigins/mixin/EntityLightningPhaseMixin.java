package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.CrossPhaseEntity;
import com.windykindy49.windyorigins.util.MirrorEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Entity.class})
public abstract class EntityLightningPhaseMixin {
    @Inject(
        method = {"onStruckByLightning"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void windyorigins$ignoreCrossPhaseBolt(ServerWorld var1, LightningEntity var2, CallbackInfo var3) {
        if (!((CrossPhaseEntity)var2).windyorigins$isCrossPhase()) {
            if (((MirrorEntity)(Object)this).windyorigins$isInMirrorWorld() != ((MirrorEntity)var2).windyorigins$isInMirrorWorld()) {
                var3.cancel();
            }
        }
    }
}
