package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.MirrorEntity;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({EnderDragonEntity.class})
public abstract class EnderDragonPhaseMixin {
    @Inject(
        method = {"launchLivingEntities", "damageLivingEntities"},
        at = {@At("HEAD")}
    )
    private void windyorigins$onlySweepSamePhase(List<Entity> var1, CallbackInfo var2) {
        boolean var3 = ((MirrorEntity)(Object)this).windyorigins$isInMirrorWorld();
        var1.removeIf(var1x -> ((MirrorEntity)var1x).windyorigins$isInMirrorWorld() != var3);
    }
}
