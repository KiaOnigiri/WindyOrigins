package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.CrossPhaseEntity;
import com.windykindy49.windyorigins.util.MirrorContext;
import com.windykindy49.windyorigins.util.MirrorEntity;
import com.windykindy49.windyorigins.util.NaturalLightning;
import com.windykindy49.windyorigins.util.SpawnPhaseOverride;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ServerWorld.class})
public class ServerWorldSpawnMixin {
    @Inject(
        method = {"spawnEntity"},
        at = {@At("HEAD")}
    )
    private void onSpawnEntity(Entity var1, CallbackInfoReturnable<Boolean> var2) {
        Boolean var3 = SpawnPhaseOverride.get();
        if (var3 != null) {
            ((MirrorEntity)var1).windyorigins$setInMirrorWorld(var3);
        } else if (var1 instanceof LightningEntity && NaturalLightning.isWeatherStrike()) {
            ((CrossPhaseEntity)var1).windyorigins$setCrossPhase(true);
        } else {
            Boolean var4 = MirrorContext.get();
            if (var4 != null) {
                ((MirrorEntity)var1).windyorigins$setInMirrorWorld(var4);
            }
        }
    }
}
