package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.MirrorEntity;
import com.windykindy49.windyorigins.util.PhaseScope;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({TntMinecartEntity.class})
public class TntMinecartEntityMixin {
    @Inject(
        method = {"prime"},
        at = {@At("HEAD")}
    )
    private void onPrimeHead(CallbackInfo var1) {
        PhaseScope.enter(((MirrorEntity)(Object)this).windyorigins$isInMirrorWorld());
    }

    @Inject(
        method = {"prime"},
        at = {@At("RETURN")}
    )
    private void onPrimeTail(CallbackInfo var1) {
        PhaseScope.exit();
    }
}
