package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.CrossPhaseEntity;
import com.windykindy49.windyorigins.util.MirrorEntity;
import com.windykindy49.windyorigins.util.SpawnPhaseOverride;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.passive.MooshroomEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({PigEntity.class, VillagerEntity.class, MooshroomEntity.class, CreeperEntity.class})
public abstract class LightningConvertMixin {
    @Unique
    private boolean windyorigins$scoped;

    @Inject(
        method = {"onStruckByLightning"},
        at = {@At("HEAD")},
        cancellable = true,
        require = 0
    )
    private void windyorigins$beginStrike(ServerWorld var1, LightningEntity var2, CallbackInfo var3) {
        if (!((CrossPhaseEntity)var2).windyorigins$isCrossPhase()
            && ((MirrorEntity)(Object)this).windyorigins$isInMirrorWorld() != ((MirrorEntity)var2).windyorigins$isInMirrorWorld()) {
            var3.cancel();
        } else {
            SpawnPhaseOverride.enter(((MirrorEntity)(Object)this).windyorigins$isInMirrorWorld());
            this.windyorigins$scoped = true;
        }
    }

    @Inject(
        method = {"onStruckByLightning"},
        at = {@At("RETURN")},
        require = 0
    )
    private void windyorigins$endStrike(ServerWorld var1, LightningEntity var2, CallbackInfo var3) {
        if (this.windyorigins$scoped) {
            this.windyorigins$scoped = false;
            SpawnPhaseOverride.exit();
        }
    }
}
